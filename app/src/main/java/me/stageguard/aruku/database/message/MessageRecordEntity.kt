package me.stageguard.aruku.database.message

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import me.stageguard.aruku.domain.data.message.MessageElement
import me.stageguard.aruku.service.parcel.ContactId
import me.stageguard.aruku.service.parcel.Message

@Parcelize
@Serializable
@Entity(
    tableName = "message_record",
    indices = [Index("account_id", "type", "subject")],
//    foreignKeys = [
//        ForeignKey(
//            entity = AccountEntity::class,
//            parentColumns = arrayOf("account_no"),
//            childColumns = arrayOf("account_id"),
//            onDelete = ForeignKey.CASCADE
//        )
//    ]
)
data class MessageRecordEntity(
    @ColumnInfo(name = "account_id") val account: Long,
    @Embedded val contact: ContactId,
    @ColumnInfo(name = "sender") val sender: Long,
    @ColumnInfo(name = "sender_name") val senderName: String,
    @PrimaryKey @ColumnInfo(name = "message_id") val messageId: Long,
    @ColumnInfo(name = "sequence") val sequence: Long,
    @ColumnInfo(name = "time") val time: Long,
    @ColumnInfo(name = "message") val message: List<MessageElement>,
) : Parcelable

fun Message.toEntity() =
    MessageRecordEntity(account, contact, sender, senderName, messageId, sequence, time, message)