package me.stageguard.aruku.database.message

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import me.stageguard.aruku.service.parcel.ArukuContact

@Serializable
@Entity(
    tableName = "message_preview",
    indices = [Index(value = ["account_id", "subject", "type"], unique = true)],
//    foreignKeys = [
//        ForeignKey(
//            entity = AccountEntity::class,
//            parentColumns = arrayOf("account_no"),
//            childColumns = arrayOf("account_id"),
//            onDelete = ForeignKey.CASCADE
//        )
//    ]
)
data class MessagePreviewEntity(
    @ColumnInfo(name = "account_id") val account: Long,
    @Embedded val contact: ArukuContact,
    @ColumnInfo(name = "time") var time: Long,
    @ColumnInfo(name = "preview") var previewContent: String,
    @ColumnInfo(name = "unread_count") var unreadCount: Int,
    @ColumnInfo(name = "message_id") var messageId: Int,
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_prim_key") val _prim_key: Int = 0,
)