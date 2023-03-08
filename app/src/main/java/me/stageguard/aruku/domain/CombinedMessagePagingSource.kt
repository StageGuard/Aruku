package me.stageguard.aruku.domain

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.database.message.MessageRecordDao
import me.stageguard.aruku.database.message.MessageRecordEntity
import me.stageguard.aruku.service.bridge.RoamingQueryBridge
import me.stageguard.aruku.service.parcel.ArukuContact
import me.stageguard.aruku.service.parcel.ArukuRoamingMessage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * combined message paging source
 *
 * This paging source will load messages from roaming query session and local databases.
 *
 * If network state is not stable(e.g. bot is offline or device has no internet connection),
 * the paging source will only loads data from database.
 *
 * Or else it will load data from roaming query session first and wait a few milliseconds for
 * loading. if timeout, it will return data from database.
 *
 * The result from roaming query session will also be stored to database if success.
 */
class CombinedMessagePagingSource(
    private val account: Long,
    private val contact: ArukuContact,
    context: CoroutineContext = EmptyCoroutineContext,
) : PagingSource<Int, MessageRecordEntity>(), KoinComponent, CoroutineScope {
    private val database: ArukuDatabase by inject()
    private val repo: MainRepository by inject()

    private val messageDao: MessageRecordDao = database.messageRecords()
    private val roamingQuerySession: RoamingQueryBridge? = repo.openRoamingQuery(account, contact)
    private val dbPagingSource: PagingSource<Int, MessageRecordEntity> by lazy {
        messageDao.getMessagesPaging(account, contact.subject, contact.type)
    }

    private var lastSeq: Int? = null
    private var lastTime: Int? = null

    override val coroutineContext = context + SupervisorJob()

    @Suppress("FoldInitializerAndIfToElvis")
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MessageRecordEntity> {
        val messageId = params.key
        val loadSize = params.loadSize

        // service is unavailable, process as database local paging source
        if (roamingQuerySession == null) return dbPagingSource.load(params)
        // avoid prepend
        if (messageId == null) return LoadResult.Page(listOf(), null, null)
        // get last message sequence
        if (lastSeq == null) lastSeq = roamingQuerySession.getLastMessageSeq()

        // first load, lastTime = null
        if (messageId == 0) {
            suspend fun firstLoadOffline(
                checkAllLoaded: Boolean = false
            ): LoadResult<Int, MessageRecordEntity> {
                val dbRecords = messageDao
                    .getLastNMessages(account, contact.subject, contact.type, loadSize)
                    .apply()

                lastTime = dbRecords.lastOrNull()?.time

                var nextKey = dbRecords.lastOrNull()?.messageId
                if (checkAllLoaded && dbRecords.size < loadSize) nextKey = null

                return LoadResult.Page(dbRecords, null, nextKey)
            }

            // lastSeq is null, load from database
            if (lastSeq == null) {
                return firstLoadOffline()
            } else {
                val roamingRecords = roamingQuerySession
                    .getMessagesBefore(lastSeq!!, loadSize, includeSeq = true)
                    ?.sortedByDescending { it.seq }

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
                time: Int,
                checkAllLoaded: Boolean = false
            ): LoadResult<Int, MessageRecordEntity> {
                val dbRecords = messageDao
                    .getLastNMessagesBefore(account, contact.subject, contact.type, time, loadSize)
                    .apply()

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
                val roamingRecords = roamingQuerySession
                    .getMessagesBefore(lastSeq!!, loadSize, includeSeq = false)
                    ?.sortedByDescending { it.seq }

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

    private suspend fun <T> Flow<List<T>>.apply(
        block: Sequence<T>.(Sequence<T>) -> Sequence<T> = { this }
    ) = map { r -> r.asSequence().run { block(this, this) }.toList() }.first()

    private suspend fun ArukuRoamingMessage.toEntity(): MessageRecordEntity {
        return MessageRecordEntity(
            account = account,
            contact = contact,
            sender = from,
            senderName = repo.getNickname(account, contact) ?: "",
            messageId = messageId,
            message = message,
            time = time
        )
    }

    override fun getRefreshKey(state: PagingState<Int, MessageRecordEntity>): Int? {
        // service is unavailable, process as database local paging source
        if (roamingQuerySession == null) return dbPagingSource.getRefreshKey(state)

        return 0
    }
}