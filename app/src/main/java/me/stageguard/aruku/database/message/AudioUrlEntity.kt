package me.stageguard.aruku.database.message

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "audio_url")
data class AudioUrlEntity(
    @PrimaryKey @ColumnInfo(name = "file_md5") val fileMd5: String,
    @ColumnInfo(name = "url") val url: String // TODO: empty means expired or not found
)