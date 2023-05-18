package me.stageguard.aruku.database.message

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.stageguard.aruku.database.BaseDao
import me.stageguard.aruku.service.parcel.ContactType

@Dao
abstract class MessageRecordDao : BaseDao<MessageRecordEntity> {
    // query all messages
    @Query("select * from message_record where account_id=:account and type=:type and subject=:subject order by time desc")
    abstract fun getMessages(
        account: Long,
        subject: Long,
        type: ContactType
    ): Flow<List<MessageRecordEntity>>

    @Query("select * from message_record where account_id=:account and type=:type and subject=:subject order by time desc")
    abstract fun getMessagesPaging(
        account: Long,
        subject: Long,
        type: ContactType
    ): PagingSource<Int, MessageRecordEntity>

    @Query("select * from message_record where account_id=:account and type=:type and subject=:subject and message_id=:messageId")
    abstract fun getExactMessage(
        account: Long,
        subject: Long,
        type: ContactType,
        messageId: Long,
    ): Flow<List<MessageRecordEntity>>

    @Query("select * from message_record where account_id=:account and type=:type and subject=:subject and time>:after order by time asc")
    abstract fun getMessagesAfterPagingAsc(
        account: Long,
        subject: Long,
        type: ContactType,
        after: Long
    ): Flow<List<MessageRecordEntity>>

    // query last n messages
    @Query("select * from message_record where account_id=:account and type=:type and subject=:subject order by time desc limit :limit")
    abstract fun getLastNMessages(
        account: Long,
        subject: Long,
        type: ContactType,
        limit: Int
    ): Flow<List<MessageRecordEntity>>

    @Query("select * from message_record where account_id=:account and type=:type and subject=:subject order by time desc limit :limit")
    abstract fun getLastNMessagesPaging(
        account: Long,
        subject: Long,
        type: ContactType,
        limit: Int
    ): PagingSource<Int, MessageRecordEntity>

    // query last n messages before a time
    @Query("select * from message_record where account_id=:account and type=:type and subject=:subject and time<:before order by time desc limit :limit")
    abstract fun getLastNMessagesBefore(
        account: Long,
        subject: Long,
        type: ContactType,
        before: Long,
        limit: Int = 20
    ): Flow<List<MessageRecordEntity>>

    @Query("select * from message_record where account_id=:account and type=:type and subject=:subject and time<:before order by time desc limit :limit")
    abstract fun getLastNMessagesBeforePaging(
        account: Long,
        subject: Long,
        type: ContactType,
        before: Long,
        limit: Int = 20
    ): PagingSource<Int, MessageRecordEntity>

//    @Query("select * from message_record where account_id=:account and type='TEMP' and subject=:group and sender=:member")
//    abstract fun getTempMessages(account: Long, group: Long, member: Long): Flow<MessageRecordEntity?>
}