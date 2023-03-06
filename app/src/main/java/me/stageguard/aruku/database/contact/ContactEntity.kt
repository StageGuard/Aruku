package me.stageguard.aruku.database.contact

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable
import net.mamoe.mirai.contact.Friend
import net.mamoe.mirai.contact.Group

@Serializable
@Entity(
    tableName = "friend",
    indices = [Index("account_id", "friend_id")],
    primaryKeys = ["account_id", "friend_id"]
)
data class FriendEntity(
    @ColumnInfo(name = "account_id") val account: Long,
    @ColumnInfo(name = "friend_id") val id: Long,
    @ColumnInfo(name = "friend_name") var name: String,
    @ColumnInfo(name = "friend_group") var group: Int,
    @ColumnInfo(name = "friend_avatar_url", defaultValue = "") var avatarUrl: String,
)

fun Friend.toFriendEntity() = FriendEntity(
    bot.id, id, nick, try {
        friendGroup.id
    } catch (_: Exception) {
        -1
    }, avatarUrl
)

@Serializable
@Entity(
    tableName = "group",
    indices = [Index("account_id", "group_id")],
    primaryKeys = ["account_id", "group_id"]
)
data class GroupEntity(
    @ColumnInfo(name = "account_id") val account: Long,
    @ColumnInfo(name = "group_id") val id: Long,
    @ColumnInfo(name = "group_name") var name: String,
    @ColumnInfo(name = "group_avatar_url", defaultValue = "") var avatarUrl: String,
)

fun Group.toGroupEntity() = GroupEntity(bot.id, id, name, avatarUrl)