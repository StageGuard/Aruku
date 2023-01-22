package me.stageguard.aruku.database.message

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import me.stageguard.aruku.domain.data.MessageElement
import me.stageguard.aruku.service.parcel.ArukuContact

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
    @Embedded val contact: ArukuContact,
    @ColumnInfo(name = "sender") val sender: Long,
    @ColumnInfo(name = "sender_name") val senderName: String,
    @PrimaryKey @ColumnInfo(name = "message_id") val messageId: Int,
    @ColumnInfo(name = "time") val time: Int,
    @ColumnInfo(name = "message") val message: List<MessageElement>,
)