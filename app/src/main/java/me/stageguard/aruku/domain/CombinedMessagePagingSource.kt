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

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MessageRecordEntity> {
        val messageId = params.key
        val loadSize = params.loadSize

        // service is unavailable, process as database local paging source
        if (roamingQuerySession == null) return dbPagingSource.load(params)

        // roaming query session equals null means we can only load data from database
        if (lastSeq == null) lastSeq = roamingQuerySession.getLastMessageSeq()

        if (messageId == null) return LoadResult.Page(listOf(), null, null)

        // first load, lastTime = null
        if (messageId == 0) {
            suspend fun firstLoadOffline(): LoadResult<Int, MessageRecordEntity> {
                val dbRecords = messageDao
                    .getLastNMessages(account, contact.subject, contact.type, loadSize)
                    .apply()

                lastTime = dbRecords.lastOrNull()?.time

                return LoadResult.Page(
                    dbRecords,
                    null,
                    if (dbRecords.size < loadSize) null else dbRecords.lastOrNull()?.messageId
                )
            }

            // no last seq, load from database
            if (lastSeq == null) {
                return firstLoadOffline()
            } else { // load from roaming query session
                val roamingRecords = roamingQuerySession
                    .getMessagesBefore(lastSeq!!, loadSize, includeSeq = true) ?: listOf()

                if (roamingRecords.isEmpty()) {
                    return firstLoadOffline()
                } else {
                    roamingRecords.last().apply {
                        lastSeq = seq
                        lastTime = time
                    }

                    val entities = roamingRecords.map { it.toEntity() }
                    coroutineScope {
                        database.suspendIO { messageDao.upsert(*entities.toTypedArray()) }
                    }

                    return LoadResult.Page(
                        entities,
                        null,
                        if (entities.size < loadSize) null else entities.last().messageId
                    )
                }
            }
        } else { // continue to load
            suspend fun loadBeforeOffline(time: Int): LoadResult<Int, MessageRecordEntity> {
                val dbRecords = messageDao
                    .getLastNMessagesBefore(account, contact.subject, contact.type, time, loadSize)
                    .apply()

                lastTime = dbRecords.lastOrNull()?.time

                return LoadResult.Page(
                    dbRecords,
                    null,
                    if (dbRecords.size < loadSize) null else dbRecords.lastOrNull()?.messageId
                )
            }

            // lastSeq is null, load offline
            if (lastSeq == null) {
                if (lastTime == null) return LoadResult.Error(
                    IllegalStateException("loadKey is not 0, lastSeq is null but lastTime is null.")
                )

                return loadBeforeOffline(lastTime!!)
            } else { // load from roaming query session
                val roamingRecords = roamingQuerySession
                    .getMessagesBefore(lastSeq!!, loadSize, includeSeq = false) ?: listOf()

                if (roamingRecords.isEmpty()) {
                    if (lastTime == null) return LoadResult.Error(
                        IllegalStateException("load key is not 0, roaming records is empty but lastTime is null.")
                    )
                    return loadBeforeOffline(lastTime!!)
                } else {
                    roamingRecords.last().apply {
                        lastSeq = seq
                        lastTime = time
                    }

                    val entities = roamingRecords.map { it.toEntity() }
                    coroutineScope {
                        database.suspendIO { messageDao.upsert(*entities.toTypedArray()) }
                    }

                    return LoadResult.Page(
                        entities,
                        null,
                        if (entities.size < loadSize) null else entities.last().messageId
                    )
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