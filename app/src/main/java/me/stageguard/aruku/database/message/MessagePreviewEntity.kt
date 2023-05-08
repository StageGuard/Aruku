package me.stageguard.aruku.database.message

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable
import me.stageguard.aruku.domain.data.message.contentToString
import me.stageguard.aruku.service.parcel.ContactId
import me.stageguard.aruku.service.parcel.Message

@Serializable
@Entity(
    tableName = "message_preview",
    indices = [Index(value = ["account_id", "subject", "type"])],
    primaryKeys = ["account_id", "subject", "type"]
)
data class MessagePreviewEntity(
    @ColumnInfo(name = "account_id") val account: Long,
    @Embedded val contact: ContactId,
    @ColumnInfo(name = "time") var time: Long,
    @ColumnInfo(name = "preview") var previewContent: String,
    @ColumnInfo(name = "unread_count") var unreadCount: Int,
    // message id is calculated by account, contact and other properties
    // so it can represents combined column (account, contact) as primary key
    @ColumnInfo(name = "message_id") var messageId: Int,
)

fun Message.toPreviewEntity(unreadCount: Int = 1) =
    MessagePreviewEntity(
        account,
        contact,
        time,
        buildString {
            append(senderName)
            append(": ")
            append(message.contentToString())
        },
        unreadCount,
        messageId
    )