package me.stageguard.aruku.domain

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.database.message.MessageRecordEntity
import me.stageguard.aruku.database.message.toEntity
import me.stageguard.aruku.service.bridge.RoamingQueryBridge
import me.stageguard.aruku.service.bridge.suspendIO
import me.stageguard.aruku.service.parcel.ContactId
import me.stageguard.aruku.service.parcel.Message
import kotlin.coroutines.CoroutineContext

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
    roamingQueryBridgeProvider: (Long, ContactId) -> RoamingQueryBridge?
) : RemoteMediator<Int, MessageRecordEntity>(), CoroutineScope {
    private val roamingQuerySession by lazy { roamingQueryBridgeProvider(account, contact) }

    private var currLastSeq = Long.MAX_VALUE
    private val seqLock = Mutex()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + SupervisorJob()

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, MessageRecordEntity>
    ): MediatorResult {
        val session0 = roamingQuerySession ?: return MediatorResult.Success(true)
        println("type: $loadType")
        println("id seq: ${state.pages.map { it.data.map { i2 -> i2.sequence } }}")

        if (loadType == LoadType.REFRESH) {
            val lastMessageId = session0.suspendIO { getLastMessageId() }
                .also { println("newest message id: $it") }
                ?: return MediatorResult.Success(true)
            val remoteSource = fetchRoamingMessage(session0, lastMessageId, 20, false)
                .also { println("remote source refresh: $it") }

            database.suspendIO { messageRecords().upsert(*remoteSource.toTypedArray()) }
            seqLock.withLock { currLastSeq = remoteSource.last().sequence }

            return MediatorResult.Success(remoteSource.isEmpty())
        }

        if (loadType == LoadType.APPEND) {
            val flattenSeq = state.pages.flatten().sortedByDescending { it.sequence }
            checkAbsentMessage(flattenSeq)

            val firstMessageId = flattenSeq.lastOrNull()?.messageId
                ?: return MediatorResult.Success(true)

            val remoteSource = fetchRoamingMessage(session0, firstMessageId, 20)
                .also { println("remote source append: $it") }

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
        println("check1aa currLastSeq: $currLastSeq, filterIndex: $filterIndex")
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
        println("check1aa updated currLastSeq: ${currLastSeq}, heads: $heads")

        heads.forEach { head ->
            println("check1aa enter head: $head")
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
                println("check1aa remote: $remoteSource")
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
            return "AbsentMessageHead(messageId=$messageId, sequence=$sequence, count=$count)"
        }
    }
}
