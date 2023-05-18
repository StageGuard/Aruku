package me.stageguard.aruku.domain

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.database.message.MessageRecordEntity
import me.stageguard.aruku.database.message.toEntity
import me.stageguard.aruku.service.bridge.RoamingQueryBridge
import me.stageguard.aruku.service.bridge.suspendIO
import me.stageguard.aruku.service.parcel.ContactId
import me.stageguard.aruku.service.parcel.Message
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


@OptIn(ExperimentalPagingApi::class)
class RoamingMessageMediator(
    val account: Long,
    val contact: ContactId,
    roamingQueryBridgeProvider: (Long, ContactId) -> RoamingQueryBridge?
) : RemoteMediator<Int, MessageRecordEntity>(), KoinComponent {
    private val database: ArukuDatabase by inject()
    private val roamingQuerySession by lazy { roamingQueryBridgeProvider(account, contact) }
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, MessageRecordEntity>
    ): MediatorResult {
        val session0 = roamingQuerySession ?: return MediatorResult.Success(endOfPaginationReached = true)

        val exclude: Boolean
        val messageId = when(loadType) {
            LoadType.REFRESH -> {
                exclude = false
                session0.suspendIO { getLastMessageId() }
            }
            LoadType.APPEND -> {
                exclude = true
                state.lastItemOrNull()?.messageId
            }
            else -> return MediatorResult.Success(endOfPaginationReached = true)
        } ?: return MediatorResult.Success(endOfPaginationReached = true)

        val messages = session0.suspendIO {
            getMessagesBefore(messageId, 20, exclude)
        } ?.map(Message::toEntity)

        if (messages.isNullOrEmpty()) return MediatorResult.Success(endOfPaginationReached = true)

        database.suspendIO {
            database.messageRecords().upsert(
                *messages.filterNot { it.sender <= 0 || it.messageId <= 0 }.toTypedArray()
            )
        }

        return MediatorResult.Success(messages.last().run { sender <= 0 || messageId <= 0 })

    }
}
