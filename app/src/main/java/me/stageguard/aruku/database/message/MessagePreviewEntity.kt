package me.stageguard.aruku.database.message

import androidx.room.*
import kotlinx.serialization.Serializable
import me.stageguard.aruku.database.ArukuMessageTypeConverter
import me.stageguard.aruku.database.account.AccountEntity
import me.stageguard.aruku.service.parcel.ArukuMessageType

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
    @ColumnInfo(name = "subject") val subject: Long,
    @ColumnInfo(name = "type")
    @TypeConverters(ArukuMessageTypeConverter::class) val type: ArukuMessageType,
    @ColumnInfo(name = "time") var time: Long,
    @ColumnInfo(name = "preview") var previewContent: String,
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_prim_key") val _prim_key: Int = 0,
)