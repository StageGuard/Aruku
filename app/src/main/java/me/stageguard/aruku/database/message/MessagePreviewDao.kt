package me.stageguard.aruku.database.message

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.stageguard.aruku.database.BaseDao
import me.stageguard.aruku.service.parcel.ArukuMessageType

@Dao
abstract class MessagePreviewDao : BaseDao<MessagePreviewEntity> {
    @Query("select * from message_preview where account_id=:account and type=:type order by time desc")
    abstract fun getMessages(account: Long, type: ArukuMessageType): Flow<List<MessagePreviewEntity>>

    @Query("select * from message_preview where account_id=:account and type=:type order by time desc limit :limit")
    abstract fun getMessages(account: Long, type: ArukuMessageType, limit: Int): Flow<List<MessagePreviewEntity>>

    @Query("select * from message_preview where account_id=:account order by time desc")
    abstract fun getMessages(account: Long): Flow<List<MessagePreviewEntity>>

    @Query("select * from message_preview where account_id=:account and subject=:subject and type=:type")
    abstract fun getExactMessagePreview(
        account: Long,
        subject: Long,
        type: ArukuMessageType
    ): List<MessagePreviewEntity>

    fun getFriendMessages(account: Long): Flow<List<MessagePreviewEntity>> =
        getMessages(account, ArukuMessageType.FRIEND)

    fun getFriendMessages(account: Long, limit: Int): Flow<List<MessagePreviewEntity>> =
        getMessages(account, ArukuMessageType.FRIEND, limit)

    fun getGroupMessages(account: Long): Flow<List<MessagePreviewEntity>> =
        getMessages(account, ArukuMessageType.GROUP)

    fun getGroupMessages(account: Long, limit: Int): Flow<List<MessagePreviewEntity>> =
        getMessages(account, ArukuMessageType.GROUP, limit)
}