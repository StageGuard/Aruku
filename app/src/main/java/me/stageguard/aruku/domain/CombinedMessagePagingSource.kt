package me.stageguard.aruku.domain

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.database.message.MessageRecordDao
import me.stageguard.aruku.database.message.MessageRecordEntity
import me.stageguard.aruku.service.bridge.RoamingQueryBridge
import me.stageguard.aruku.service.bridge.suspendIO
import me.stageguard.aruku.service.parcel.ContactId
import me.stageguard.aruku.service.parcel.ArukuRoamingMessage
import me.stageguard.aruku.util.createAndroidLogger
import net.mamoe.mirai.utils.ConcurrentLinkedQueue
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * combined message paging source
 * which load messages from history source and subscription source
 */
class CombinedMessagePagingSource(
    private val account: Long,
    private val contact: ContactId,
    context: CoroutineContext = EmptyCoroutineContext,
) : PagingSource<Long, MessageRecordEntity>(), KoinComponent, CoroutineScope {
    private val logger = createAndroidLogger("CombinedMessagePagingSource")
    private val database: ArukuDatabase by inject()
    private val messageDao = database.messageRecords()

    override val coroutineContext: CoroutineContext = context + Job()

    private lateinit var subscriptionSource: Flow<List<MessageRecordEntity>>
    private val historySource: HistoryMessagePagingSource by lazy {
        HistoryMessagePagingSource(account, contact, coroutineContext)
    }

    // append key is message id
    private var lastAppendKey: Int? = null
    private var lastSubscribedTime: Long? = null

    private var subscribedMessageCount = 0
    private var historyMessageCount = 0

    private val subscriptionMessageCache = ConcurrentLinkedQueue<MessageRecordEntity>()
    private val subscriptionSourceRenewLock = Mutex(true)
    private val subscriptionCacheAccessLock = Mutex(false)

    private val subscriptionLoopJob: Job = launch(start = CoroutineStart.LAZY) {
        var subscriptionJob: Job

        while (isActive) {
            subscriptionJob = launch(Dispatchers.IO) {
                subscriptionSource.cancellable().collect { records ->
                    if (records.isEmpty()) return@collect

                    records.forEach(subscriptionMessageCache::offer)
                    lastSubscribedTime = records.last().time

                    subscriptionCacheAccessLock.apply { if (isLocked) unlock() }
                }
            }

            subscriptionSourceRenewLock.lock()
            subscriptionJob.cancelAndJoin()

            subscriptionSource = messageDao.getMessagesAfterPagingAsc(
                account, contact.subject, contact.type, lastSubscribedTime!!
            )
        }
    }

    override val keyReuseSupported: Boolean = true

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, MessageRecordEntity> {
        val halfSize = params.loadSize / 2
        logger.d("call load. params=${params.toString().substringAfterLast('$')}, key=${params.key}")

        when (params) {
            is LoadParams.Refresh -> {
                return when (val historyResult = historySource.load(
                    LoadParams.Refresh(0, halfSize, params.placeholdersEnabled)
                )) {
                    is LoadResult.Page -> {
                        lastSubscribedTime = historyResult.first().time
                        historyMessageCount = historyResult.data.size

                        subscriptionSource = messageDao.getMessagesAfterPagingAsc(
                            account, contact.subject, contact.type, lastSubscribedTime!!
                        )
                        subscriptionLoopJob.start()

                        lastSubscribedTime = SUBSCRIPTION_LOCK_ACQUIRING
                        lastAppendKey = historyResult.nextKey

                        LoadResult.Page(
                            data = historyResult.data,
                            prevKey = lastSubscribedTime,
                            nextKey = lastAppendKey?.toLong(),
                            itemsBefore = 0,
                            itemsAfter = 0
                        )
                    }

                    is LoadResult.Invalid -> LoadResult.Invalid()
                    is LoadResult.Error -> LoadResult.Error(
                        IllegalStateException(
                            "refresh load of history source fails.",
                            historyResult.throwable
                        )
                    )
                }
            }

            is LoadParams.Prepend -> {
                val key = params.key
                if (key == SUBSCRIPTION_LOCK_ACQUIRING) subscriptionCacheAccessLock.lock()

                var poll: MessageRecordEntity? = subscriptionMessageCache.poll()
                    ?: return LoadResult.Page(
                        data = listOf(),
                        prevKey = SUBSCRIPTION_LOCK_ACQUIRING,
                        nextKey = lastAppendKey?.toLong(),
                        itemsBefore = 0,
                        itemsAfter = subscribedMessageCount + historyMessageCount
                    )

                val cached = mutableListOf<MessageRecordEntity>()
                while (poll != null) {
                    cached.add(poll)
                    poll = subscriptionMessageCache.poll()
                }

                subscriptionSourceRenewLock.apply { if (isLocked) unlock() }

                subscribedMessageCount += cached.size

                return LoadResult.Page(
                    data = cached,
                    prevKey = SUBSCRIPTION_LOCK_ACQUIRING,
                    nextKey = lastAppendKey?.toLong(),
                    itemsBefore = 0,
                    itemsAfter = subscribedMessageCount + historyMessageCount
                )
            }

            is LoadParams.Append -> {
                return when (val historyResult = historySource.load(
                    LoadParams.Append(params.key.toInt(), halfSize, params.placeholdersEnabled)
                )) {
                    is LoadResult.Page -> {
                        historyMessageCount += historyResult.data.size

                        lastAppendKey = historyResult.nextKey

                        LoadResult.Page(
                            data = historyResult.data,
                            prevKey = lastSubscribedTime,
                            nextKey = lastAppendKey?.toLong(),
                            itemsBefore = subscribedMessageCount + historyMessageCount,
                            itemsAfter = 0
                        )

                    }

                    is LoadResult.Invalid -> LoadResult.Invalid()
                    is LoadResult.Error -> LoadResult.Error(
                        IllegalStateException(
                            "refresh load of history source fails.",
                            historyResult.throwable
                        )
                    )
                }
            }
        }
    }

    override fun getRefreshKey(state: PagingState<Long, MessageRecordEntity>): Long = 0

    companion object {
        private const val SUBSCRIPTION_LOCK_ACQUIRING = -1L
    }
}

/**
 * history message paging source
 *
 * This paging source will load history messages from roaming query session and databases.
 *
 * If network state is not stable(e.g. bot is offline or device has no internet connection),
 * it will only loads messages from the database.
 *
 * Or else it will load messages from roaming query session first
 * and fallback to database if fails.
 *
 * Note that this paging source only appends
 *
 * The result from roaming query session will be stored to database if success.
 */
class HistoryMessagePagingSource(
    private val account: Long,
    private val contact: ContactId,
    context: CoroutineContext = EmptyCoroutineContext,
) : PagingSource<Int, MessageRecordEntity>(), KoinComponent, CoroutineScope {
    private val logger = createAndroidLogger("HistoryMessagePagingSource")
    private val database: ArukuDatabase by inject()
    private val repo: MainRepository by inject()

    private val messageDao: MessageRecordDao = database.messageRecords()
    private val roamingQuerySession: RoamingQueryBridge? = repo.openRoamingQuery(account, contact)
    private val historyDbSource: PagingSource<Int, MessageRecordEntity> by lazy {
        messageDao.getMessagesPaging(account, contact.subject, contact.type)
    }

    private var lastSeq: Int? = null
    private var lastTime: Long? = null

    override val coroutineContext = context + SupervisorJob()

    @Suppress("FoldInitializerAndIfToElvis")
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MessageRecordEntity> {
        logger.d("call load. params=${params.toString().substringAfterLast('$')}, key=${params.key}")

        val messageId = params.key
        val loadSize = params.loadSize

        // service is unavailable, process as database local paging source
        if (roamingQuerySession == null) return historyDbSource.load(params)
        // avoid prepend
        if (messageId == null) return LoadResult.Page(listOf(), null, null)
        // get last message sequence
        if (lastSeq == null) lastSeq = roamingQuerySession.suspendIO { getLastMessageSeq() }

        // first load, lastTime = null
        if (messageId == 0) {
            suspend fun firstLoadOffline(
                checkAllLoaded: Boolean = false
            ): LoadResult<Int, MessageRecordEntity> {
                val dbRecords = database.suspendIO {
                    messageDao
                        .getLastNMessages(account, contact.subject, contact.type, loadSize)
                        .apply()
                }.first()

                lastTime = dbRecords.lastOrNull()?.time

                var nextKey = dbRecords.lastOrNull()?.messageId
                if (checkAllLoaded && dbRecords.size < loadSize) nextKey = null

                return LoadResult.Page(dbRecords, null, nextKey)
            }

            // lastSeq is null, load from database
            if (lastSeq == null) {
                return firstLoadOffline()
            } else {
                val roamingRecords = roamingQuerySession.suspendIO {
                    getMessagesBefore(lastSeq!!, loadSize, includeSeq = true)
                        ?.sortedByDescending { it.seq }
                }

                // first load from roaming query session fails, load offline
                if (roamingRecords == null) return firstLoadOffline()

                // first load but get nothing from roaming query session,
                // we assume that nothing will return from roaming query session
                // begin to check if database cache are all loaded.
                // but also we will try to load from roaming query session again
                // until all database cache are loaded.
                if (roamingRecords.isEmpty()) {
                    if (lastSeq == null) return LoadResult.Error(
                        IllegalStateException("first load, roaming records is empty but lastSeq is null.")
                    )
                    lastSeq = lastSeq!! - loadSize
                    return firstLoadOffline(checkAllLoaded = true)
                } else {
                    val filtered = roamingRecords.filter { it.messageId != 0 }

                    // first load successful from roaming query session,
                    // but all messages are invalid
                    // skip these sequence of messages and load offline
                    if (filtered.isEmpty()) {
                        if (lastTime == null) return LoadResult.Error(
                            IllegalStateException("first load, roaming records is not empty and filtered is empty but lastTime is null.")
                        )
                        lastSeq = lastSeq!! - roamingRecords.size
                        return firstLoadOffline()
                    }

                    // first load successful from roaming query session
                    filtered.last().apply {
                        lastSeq = seq - 1
                        // update lastTime in case to load offline as expected
                        // if continue load from roaming query session fails
                        lastTime = time
                    }

                    val entities = filtered.map { it.toEntity() }
                    coroutineScope {
                        database.suspendIO { messageDao.upsert(*entities.toTypedArray()) }
                    }

                    return LoadResult.Page(
                        entities, null, entities.last().messageId
                    )
                }
            }
        } else { // continue to load
            suspend fun loadBeforeOffline(
                time: Long,
                checkAllLoaded: Boolean = false
            ): LoadResult<Int, MessageRecordEntity> {
                val dbRecords = database.suspendIO {
                    messageDao.getLastNMessagesBefore(
                        account,
                        contact.subject,
                        contact.type,
                        time,
                        loadSize
                    ).apply()
                }.first()

                lastTime = dbRecords.lastOrNull()?.time

                var nextKey = dbRecords.lastOrNull()?.messageId
                if (checkAllLoaded && dbRecords.size < loadSize) nextKey = null

                return LoadResult.Page(dbRecords, null, nextKey)
            }

            // lastSeq is null, load offline
            if (lastSeq == null) {
                if (lastTime == null) return LoadResult.Error(
                    IllegalStateException("loadKey is not 0, lastSeq is null but lastTime is null.")
                )

                return loadBeforeOffline(lastTime!!)
            } else {
                val roamingRecords = roamingQuerySession.suspendIO {
                    getMessagesBefore(lastSeq!!, loadSize, includeSeq = false)
                        ?.sortedByDescending { it.seq }
                }

                // continue load from roaming query session fails,
                // skip these seq to avoid multiple load same messages and load offline
                if (roamingRecords == null) {
                    if (lastTime == null) return LoadResult.Error(
                        IllegalStateException("continue load, roaming records is null but lastTime is null.")
                    )
                    // continue load ensures the seq is not null
                    lastSeq = lastSeq!! - loadSize
                    return loadBeforeOffline(lastTime!!)
                }

                // continue load but get nothing from roaming query session
                // we assume that all roaming messages are loaded,
                // begin to check if database cache are all loaded.
                if (roamingRecords.isEmpty()) {
                    if (lastTime == null) return LoadResult.Error(
                        IllegalStateException("continue load, roaming records is empty but lastTime is null.")
                    )
                    return loadBeforeOffline(lastTime!!, checkAllLoaded = true)
                } else {
                    val filtered = roamingRecords.filter { it.messageId != 0 }

                    // continue load successful from roaming query session,
                    // but all messages are invalid
                    // skip these seq and load offline
                    if (filtered.isEmpty()) {
                        if (lastTime == null) return LoadResult.Error(
                            IllegalStateException("continue load, roaming records is not empty and filtered is empty but lastTime is null.")
                        )
                        lastSeq = lastSeq!! - roamingRecords.size
                        return loadBeforeOffline(lastTime!!)
                    }

                    // continue load successful from roaming query session
                    filtered.last().apply {
                        lastSeq = seq - 1
                        // update lastTime in case to load offline as expected
                        // if continue load from roaming query session fails
                        lastTime = time
                    }

                    val entities = filtered.map { it.toEntity() }
                    coroutineScope {
                        database.suspendIO { messageDao.upsert(*entities.toTypedArray()) }
                    }

                    return LoadResult.Page(entities, null, entities.last().messageId)
                }
            }
        }
    }

    private fun <T> Flow<List<T>>.apply(
        block: Sequence<T>.(Sequence<T>) -> Sequence<T> = { this }
    ) = map { r -> r.asSequence().run { block(this, this) }.toList() }

    private fun ArukuRoamingMessage.toEntity(): MessageRecordEntity {
        return MessageRecordEntity(
            account = account,
            contact = contact,
            sender = from,
            senderName = repo.getGroupMemberInfo(account, contact.subject, from)?.senderName ?: "",
            messageId = messageId,
            message = message,
            time = time
        )
    }

    override fun getRefreshKey(state: PagingState<Int, MessageRecordEntity>): Int? {
        // service is unavailable, process as database local paging source
        if (roamingQuerySession == null) return historyDbSource.getRefreshKey(state)

        return 0
    }
}