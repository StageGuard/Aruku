package me.stageguard.aruku.database.message

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.stageguard.aruku.database.BaseDao
import me.stageguard.aruku.service.parcel.ArukuMessageType

@Dao
abstract class MessageRecordDao : BaseDao<MessageRecordEntity> {
    // query all messages
    @Query("select * from message_record where bot=:bot and type=:type and subject=:subject")
    abstract fun getMessages(bot: Long, subject: Long, type: Int): Flow<List<MessageRecordEntity>>

    // query last n messages
    @Query("select * from message_record where bot=:bot and type=:type and subject=:subject order by time desc limit :num")
    abstract fun getLastNMessages(bot: Long, subject: Long, type: Int, num: Int): Flow<List<MessageRecordEntity>>

    // query last n messages before a time
    @Query("select * from message_record where bot=:bot and type=:type and subject=:subject and time<:before order by time desc limit :num")
    abstract fun getLastNMessagesBefore(
        bot: Long,
        subject: Long,
        type: Int,
        num: Int = 20,
        before: Int
    ): Flow<List<MessageRecordEntity>>

    // group
    fun getGroupMessages(bot: Long, group: Long) = getMessages(bot, group, ArukuMessageType.GROUP.ordinal)

    fun getLastNGroupMessages(bot: Long, group: Long, num: Int = 20) =
        getLastNMessages(bot, group, ArukuMessageType.GROUP.ordinal, num)

    fun getLastNGroupMessagesBefore(bot: Long, group: Long, num: Int = 20, before: Int) =
        getLastNMessagesBefore(bot, group, ArukuMessageType.GROUP.ordinal, num, before)


    // friend
    fun getFriendMessages(bot: Long, friend: Long) = getMessages(bot, friend, ArukuMessageType.FRIEND.ordinal)

    fun getLastNFriendMessages(bot: Long, friend: Long, num: Int = 20) =
        getLastNMessages(bot, friend, ArukuMessageType.FRIEND.ordinal, num)

    fun getLastNFriendMessagesBefore(bot: Long, friend: Long, num: Int = 20, before: Int) =
        getLastNMessagesBefore(bot, friend, ArukuMessageType.FRIEND.ordinal, num, before)

//    @Query("select * from message_record where bot=:bot and type='TEMP' and subject=:group and sender=:member")
//    abstract fun getTempMessages(bot: Long, group: Long, member: Long): Flow<MessageRecordEntity?>
}