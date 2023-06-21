package me.stageguard.aruku.domain

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.stageguard.aruku.common.createAndroidLogger
import me.stageguard.aruku.common.service.bridge.RoamingQueryBridge
import me.stageguard.aruku.common.service.bridge.suspendIO
import me.stageguard.aruku.common.service.parcel.ContactId
import me.stageguard.aruku.common.service.parcel.Message
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.database.message.MessageRecordEntity
import me.stageguard.aruku.database.message.toEntity
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * fetch absent message by message sequence.
 *
 * sequence is dispatched by server, for every group
 * sequence is unique and incremental which represents message sequence.
 */
@OptIn(ExperimentalPagingApi::class)
class SequenceRoamingMessageMediator(
    val account: Long,
    val contact: ContactId,
    private val database: ArukuDatabase,
    parentCoroutineContext: CoroutineContext? = EmptyCoroutineContext,
    roamingQueryBridgeProvider: (Long, ContactId) -> RoamingQueryBridge?
) : RemoteMediator<Int, MessageRecordEntity>(), CoroutineScope {
    private val logger = createAndroidLogger()

    private val roamingQuerySession by lazy { roamingQueryBridgeProvider(account, contact) }

    private var currLastSeq = Long.MAX_VALUE
    private val seqLock = Mutex()

    override val coroutineContext: CoroutineContext =
        Dispatchers.IO + SupervisorJob(parentCoroutineContext?.get(Job))

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, MessageRecordEntity>
    ): MediatorResult {
        val session0 = roamingQuerySession ?: return MediatorResult.Success(true)

        if (loadType == LoadType.REFRESH) {
            // if cannot get last sequence, just set the last in database as the sequence.
            val lastMessageId = session0.suspendIO { getLastMessageId() }
                .also { logger.d("load refresh roaming get last message id = $it") }
                ?: return MediatorResult.Success(false)

            val remoteSource = fetchRoamingMessage(session0, lastMessageId, 20, false)
            logger.d(buildString {
                append("load refresh roaming remote source: size = ${remoteSource.size}")
                if (remoteSource.isNotEmpty()) {
                    append(", seq from ${remoteSource.first().sequence} to ${remoteSource.last().sequence}")
                }
            })

            database.suspendIO { messageRecords().upsert(*remoteSource.toTypedArray()) }
            seqLock.withLock { currLastSeq = remoteSource.last().sequence }

            return MediatorResult.Success(remoteSource.isEmpty())
        }

        if (loadType == LoadType.APPEND) {
            val pageRecords = state.pages.flatten().sortedByDescending { it.sequence }
            if (pageRecords.isEmpty()) return MediatorResult.Success(true)

            // if cannot get last sequence, just set the last in database as the sequence.
            if (currLastSeq == Long.MAX_VALUE) {
                currLastSeq = pageRecords.first().messageId
            }
            checkAbsentMessage(pageRecords)

            val firstMessageId = pageRecords.last().messageId
            val remoteSource = fetchRoamingMessage(session0, firstMessageId, 20)
            logger.d(buildString {
                append("load append roaming remote source: size = ${remoteSource.size}")
                if (remoteSource.isNotEmpty()) {
                    append(", seq from ${remoteSource.first().sequence} to ${remoteSource.last().sequence}")
                }
            })

            database.suspendIO { messageRecords().upsert(*remoteSource.toTypedArray()) }
            return MediatorResult.Success(remoteSource.isEmpty())
        }

        return MediatorResult.Success(true)
    }

    private fun checkAbsentMessage(sequence: List<MessageRecordEntity>) = launch {
        if (sequence.size <= 1) return@launch
        val session0 = roamingQuerySession ?: return@launch

        seqLock.lock()

        val filterIndex = sequence.asReversed().indexOfFirst { it.sequence == currLastSeq }
        logger.d("check absent currLastSeq: $currLastSeq, filterIndex: $filterIndex")
        if (filterIndex <= 0) {
            seqLock.unlock()
            return@launch
        }
        val subIndex = sequence.size - filterIndex - 1 // sub list start

        val heads = mutableListOf<AbsentMessageHead>()
        var current = sequence[subIndex - 1].sequence

        sequence.subList(subIndex, sequence.lastIndex).forEachIndexed { i, e ->
            if (e.sequence + 1 < current) {
                val prev = sequence[subIndex + i - 1]
                val diff = (current - e.sequence).toInt() - 1
                heads.add(AbsentMessageHead(prev.messageId, prev.sequence, diff))
            }

            current = e.sequence
        }

        currLastSeq = minOf(currLastSeq, sequence.last().sequence)
        logger.d("check absent updated currLastSeq: ${currLastSeq}, heads: $heads")

        heads.forEach { head ->
            if (head.count <= 20) {
                val remoteSource = fetchRoamingMessage(session0, head.messageId, head.count)
                database.suspendIO { messageRecords().upsert(*remoteSource.toTypedArray()) }
                return@forEach
            }

            var remain = head.count
            var messageId = head.messageId
            while (remain > 0) {
                val fetchSize = remain.coerceAtMost(20)

                val remoteSource = fetchRoamingMessage(session0, messageId, fetchSize)
                logger.d(buildString {
                    append("load refresh roaming remote source: size = ${remoteSource.size}")
                    if (remoteSource.isNotEmpty()) {
                        append(", seq from ${remoteSource.first().sequence} to ${remoteSource.last().sequence}")
                    }
                })
                if (remoteSource.isEmpty()) break

                database.suspendIO { messageRecords().upsert(*remoteSource.toTypedArray()) }

                remain -= remoteSource.size
                messageId = remoteSource.last().messageId
            }

        }
        seqLock.unlock()
    }

    private suspend fun fetchRoamingMessage(
        session: RoamingQueryBridge,
        fromMessageId: Long,
        size: Int,
        exclude: Boolean = true
    ): List<MessageRecordEntity> {
        return session
            .suspendIO { getMessagesBefore(fromMessageId, size, exclude) }
            .orEmpty()
            .filter { it.messageId != -1L && it.sender >= 10000 }
            .map(Message::toEntity)
    }

    private inner class AbsentMessageHead(val messageId: Long, val sequence: Long, val count: Int) {
        override fun toString(): String {
            return "AbsentHead(messageId=$messageId, sequence=$sequence, count=$count)"
        }
    }
}
