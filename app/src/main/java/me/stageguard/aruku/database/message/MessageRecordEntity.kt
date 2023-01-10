package me.stageguard.aruku.database.message

import androidx.room.*
import kotlinx.serialization.Serializable
import me.stageguard.aruku.database.ArukuMessageTypeConverter
import me.stageguard.aruku.service.parcel.ArukuContactType

@Serializable
@Entity(
    tableName = "message_record",
    indices = [Index("account_id", "subject", "type")],
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
    @ColumnInfo(name = "type")
    @TypeConverters(ArukuMessageTypeConverter::class) val type: ArukuContactType,
    @ColumnInfo(name = "subject") val subject: Long,
    @ColumnInfo(name = "sender") val sender: Long,
    @ColumnInfo(name = "senderName") val senderName: String,
    @PrimaryKey @ColumnInfo(name = "message_ids") val messageIds: String,
    @ColumnInfo(name = "message_internalIds") val messageInternalIds: String,
    @ColumnInfo(name = "time") val time: Int,
    @ColumnInfo(name = "message") val message: String
)