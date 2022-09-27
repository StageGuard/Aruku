package me.stageguard.aruku.database.message

import androidx.room.*
import kotlinx.serialization.Serializable
import me.stageguard.aruku.service.parcel.ArukuMessageType

@Serializable
@Entity(tableName = "message_record", indices = [Index("bot", "subject", "sender")])
data class MessageRecordEntity(
    @ColumnInfo(name = "bot") val bot: Long,
    @ColumnInfo(name = "type")
    @TypeConverters(ArukuMessageTypeConverter::class) val type: ArukuMessageType,
    @ColumnInfo(name = "subject") val subject: Long,
    @ColumnInfo(name = "sender") val sender: Long,
    @ColumnInfo(name = "senderName") val senderName: String,
    @PrimaryKey @ColumnInfo(name = "message_ids") val messageIds: String,
    @ColumnInfo(name = "message_internalIds") val messageInternalIds: String,
    @ColumnInfo(name = "time") val time: Int,
    @ColumnInfo(name = "message") val message: String
)

class ArukuMessageTypeConverter {
    @TypeConverter
    fun toMessageType(value: Int) = enumValues<ArukuMessageType>()[value]

    @TypeConverter
    fun fromMessageType(value: ArukuMessageType) = value.ordinal
}