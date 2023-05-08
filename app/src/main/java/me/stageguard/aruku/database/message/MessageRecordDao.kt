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

    // group
    fun getGroupMessages(account: Long, group: Long) =
        getMessages(account, group, ContactType.GROUP)

    fun getLastNGroupMessages(account: Long, group: Long, limit: Int = 20) =
        getLastNMessages(account, group, ContactType.GROUP, limit)

    fun getLastNGroupMessagesBefore(account: Long, group: Long, before: Long, limit: Int = 20) =
        getLastNMessagesBefore(account, group, ContactType.GROUP, before, limit)


    fun getGroupMessagesPaging(account: Long, group: Long) =
        getMessagesPaging(account, group, ContactType.GROUP)

    fun getLastNGroupMessagesPaging(account: Long, group: Long, limit: Int = 20) =
        getLastNMessagesPaging(account, group, ContactType.GROUP, limit)

    fun getLastNGroupMessagesBeforePaging(
        account: Long,
        group: Long,
        before: Long,
        limit: Int = 20
    ) =
        getLastNMessagesBeforePaging(account, group, ContactType.GROUP, before, limit)


    // friend
    fun getFriendMessages(account: Long, friend: Long) =
        getMessages(account, friend, ContactType.FRIEND)

    fun getLastNFriendMessages(account: Long, friend: Long, limit: Int = 20) =
        getLastNMessages(account, friend, ContactType.FRIEND, limit)

    fun getLastNFriendMessagesBefore(account: Long, friend: Long, before: Long, limit: Int = 20) =
        getLastNMessagesBefore(account, friend, ContactType.FRIEND, before, limit)

    fun getFriendMessagesPaging(account: Long, friend: Long) =
        getMessagesPaging(account, friend, ContactType.FRIEND)

    fun getLastNFriendMessagesPaging(account: Long, friend: Long, limit: Int = 20) =
        getLastNMessagesPaging(account, friend, ContactType.FRIEND, limit)

    fun getLastNFriendMessagesBeforePaging(
        account: Long,
        friend: Long,
        before: Long,
        limit: Int = 20
    ) = getLastNMessagesBeforePaging(account, friend, ContactType.FRIEND, before, limit)

//    @Query("select * from message_record where account_id=:account and type='TEMP' and subject=:group and sender=:member")
//    abstract fun getTempMessages(account: Long, group: Long, member: Long): Flow<MessageRecordEntity?>
}