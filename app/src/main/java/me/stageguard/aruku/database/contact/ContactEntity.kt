package me.stageguard.aruku.database.contact

import androidx.room.*
import kotlinx.serialization.Serializable
import me.stageguard.aruku.database.account.AccountEntity

@Serializable
@Entity(
    tableName = "friend",
    indices = [Index("account_id", "friend_id")],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = arrayOf("account_no"),
            childColumns = arrayOf("account_id"),
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FriendEntity(
    @ColumnInfo(name = "account_id") val account: Long,
    @ColumnInfo(name = "friend_id") val id: Long,
    @ColumnInfo(name = "friend_name") var name: String,
    @ColumnInfo(name = "friend_group") var group: Int,
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_prim_key") val _prim_key: Int = 0,
)

@Serializable
@Entity(
    tableName = "group",
    indices = [Index("account_id", "group_id")],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = arrayOf("account_no"),
            childColumns = arrayOf("account_id"),
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class GroupEntity(
    @ColumnInfo(name = "account_id") val account: Long,
    @ColumnInfo(name = "group_id") val id: Long,
    @ColumnInfo(name = "group_name") var name: String,
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_prim_key") val _prim_key: Int = 0,
)