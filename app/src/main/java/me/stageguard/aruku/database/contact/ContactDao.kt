package me.stageguard.aruku.database.contact

import androidx.room.Dao
import androidx.room.Query
import me.stageguard.aruku.database.BaseDao

@Dao
abstract class GroupDao : BaseDao<GroupEntity> {
    @Query("select * from `group` where account_id=:account")
    abstract fun getGroups(account: Long): List<GroupEntity>
}

@Dao
abstract class FriendDao : BaseDao<FriendEntity> {
    @Query("select * from friend where account_id=:account")
    abstract fun getFriends(account: Long): List<FriendEntity>
}