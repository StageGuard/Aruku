package me.stageguard.aruku.database.message

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.stageguard.aruku.database.BaseDao
import me.stageguard.aruku.service.parcel.ArukuContactType

@Dao
abstract class MessageRecordDao : BaseDao<MessageRecordEntity> {
    // query all messages
    @Query("select * from message_record where account_id=:account and type=:type and subject=:subject")
    abstract fun getMessages(account: Long, subject: Long, type: Int): Flow<List<MessageRecordEntity>>

    @Query("select * from message_record where account_id=:account and type=:type and subject=:subject")
    abstract fun getMessagesPaging(account: Long, subject: Long, type: Int): PagingSource<Int, MessageRecordEntity>

    // query last n messages
    @Query("select * from message_record where account_id=:account and type=:type and subject=:subject order by time desc limit :limit")
    abstract fun getLastNMessages(account: Long, subject: Long, type: Int, limit: Int): Flow<List<MessageRecordEntity>>

    @Query("select * from message_record where account_id=:account and type=:type and subject=:subject order by time desc limit :limit")
    abstract fun getLastNMessagesPaging(
        account: Long,
        subject: Long,
        type: Int,
        limit: Int
    ): PagingSource<Int, MessageRecordEntity>

    // query last n messages before a time
    @Query("select * from message_record where account_id=:account and type=:type and subject=:subject and time<:before order by time desc limit :limit")
    abstract fun getLastNMessagesBefore(
        account: Long,
        subject: Long,
        type: Int,
        limit: Int = 20,
        before: Int
    ): Flow<List<MessageRecordEntity>>

    @Query("select * from message_record where account_id=:account and type=:type and subject=:subject and time<:before order by time desc limit :limit")
    abstract fun getLastNMessagesBeforePaging(
        account: Long,
        subject: Long,
        type: Int,
        limit: Int = 20,
        before: Int
    ): PagingSource<Int, MessageRecordEntity>

    // group
    fun getGroupMessages(account: Long, group: Long) = getMessages(account, group, ArukuContactType.GROUP.ordinal)

    fun getLastNGroupMessages(account: Long, group: Long, limit: Int = 20) =
        getLastNMessages(account, group, ArukuContactType.GROUP.ordinal, limit)

    fun getLastNGroupMessagesBefore(account: Long, group: Long, limit: Int = 20, before: Int) =
        getLastNMessagesBefore(account, group, ArukuContactType.GROUP.ordinal, limit, before)


    fun getGroupMessagesPaging(account: Long, group: Long) =
        getMessagesPaging(account, group, ArukuContactType.GROUP.ordinal)

    fun getLastNGroupMessagesPaging(account: Long, group: Long, limit: Int = 20) =
        getLastNMessagesPaging(account, group, ArukuContactType.GROUP.ordinal, limit)

    fun getLastNGroupMessagesBeforePaging(account: Long, group: Long, limit: Int = 20, before: Int) =
        getLastNMessagesBeforePaging(account, group, ArukuContactType.GROUP.ordinal, limit, before)


    // friend
    fun getFriendMessages(account: Long, friend: Long) = getMessages(account, friend, ArukuContactType.FRIEND.ordinal)

    fun getLastNFriendMessages(account: Long, friend: Long, limit: Int = 20) =
        getLastNMessages(account, friend, ArukuContactType.FRIEND.ordinal, limit)

    fun getLastNFriendMessagesBefore(account: Long, friend: Long, limit: Int = 20, before: Int) =
        getLastNMessagesBefore(account, friend, ArukuContactType.FRIEND.ordinal, limit, before)

    fun getFriendMessagesPaging(account: Long, friend: Long) =
        getMessagesPaging(account, friend, ArukuContactType.FRIEND.ordinal)

    fun getLastNFriendMessagesPaging(account: Long, friend: Long, limit: Int = 20) =
        getLastNMessagesPaging(account, friend, ArukuContactType.FRIEND.ordinal, limit)

    fun getLastNFriendMessagesBeforePaging(account: Long, friend: Long, limit: Int = 20, before: Int) =
        getLastNMessagesBeforePaging(account, friend, ArukuContactType.FRIEND.ordinal, limit, before)

//    @Query("select * from message_record where account_id=:account and type='TEMP' and subject=:group and sender=:member")
//    abstract fun getTempMessages(account: Long, group: Long, member: Long): Flow<MessageRecordEntity?>
}