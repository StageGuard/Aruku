package me.stageguard.aruku.database.contact

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable
import me.stageguard.aruku.service.parcel.ContactId
import me.stageguard.aruku.service.parcel.ContactInfo

@Serializable
@Entity(
    tableName = "contact",
    indices = [Index("account_id", "subject")],
    primaryKeys = ["account_id", "subject"]
)
data class ContactEntity(
    @ColumnInfo(name = "account_id") val account: Long,
    @Embedded val contact: ContactId,
    @ColumnInfo(name = "name") var name: String,
    @ColumnInfo(name = "avatar_url", defaultValue = "") var avatarUrl: String,
)

fun ContactInfo.toEntity(account: Long) =
    ContactEntity(account, id, name ?: "", avatarUrl ?: "")