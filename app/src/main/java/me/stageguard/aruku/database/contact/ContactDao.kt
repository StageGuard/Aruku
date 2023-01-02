package me.stageguard.aruku.database.contact

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.stageguard.aruku.database.BaseDao

@Dao
abstract class GroupDao : BaseDao<GroupEntity> {
    @Query("select * from `group` where account_id=:account")
    abstract fun getGroups(account: Long): List<GroupEntity>

    @Query("select * from `group` where account_id=:account")
    abstract fun getGroupsPaging(account: Long): PagingSource<Int, GroupEntity>

    @Query("select * from `group` where account_id=:account")
    abstract fun getGroupsFlow(account: Long): Flow<List<GroupEntity>>

    @Query("select * from `group` where account_id=:account and group_id=:id")
    abstract fun getGroup(account: Long, id: Long): List<GroupEntity>

    @Query("delete from `group` where account_id=:account and group_id=:id")
    abstract fun deleteViaId(account: Long, id: Long)
}

@Dao
abstract class FriendDao : BaseDao<FriendEntity> {
    @Query("select * from friend where account_id=:account")
    abstract fun getFriends(account: Long): List<FriendEntity>

    @Query("select * from friend where account_id=:account")
    abstract fun getFriendsPaging(account: Long): PagingSource<Int, FriendEntity>

    @Query("select * from friend where account_id=:account")
    abstract fun getFriendsFlow(account: Long): Flow<List<FriendEntity>>

    @Query("select * from friend where account_id=:account and friend_id=:id")
    abstract fun getFriend(account: Long, id: Long): List<FriendEntity>

    @Query("delete from friend where account_id=:account and friend_id=:id")
    abstract fun deleteViaId(account: Long, id: Long)
}