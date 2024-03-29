package me.stageguard.aruku.common.message

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class Audio(
    val url: String,
    val length: Long,
    val fileName: String,
    val fileMd5: String,
    val fileSize: Long,
    val extension: String,
    val downloadUrl: String? = null, // TODO: remove default value
) : MessageElement, Parcelable {
    override fun contentToString(): String {
        return "[语音]"
    }
}

@Parcelize
@Serializable
data class File(
    val id: String?,
    val url: String?,
    val name: String,
    val md5: ByteArray?,
    val extension: String?,
    val size: Long,
    val expiryTime: Long?,
) : MessageElement, Parcelable {
    override fun contentToString(): String {
        return "[文件]$name.$extension"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as File

        if (id != other.id) return false
        if (url != other.url) return false
        if (name != other.name) return false
        if (md5 != null) {
            if (other.md5 == null) return false
            if (!md5.contentEquals(other.md5)) return false
        } else if (other.md5 != null) return false
        if (extension != other.extension) return false
        if (size != other.size) return false
        return expiryTime == other.expiryTime
    }

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + (url?.hashCode() ?: 0)
        result = 31 * result + name.hashCode()
        result = 31 * result + (md5?.contentHashCode() ?: 0)
        result = 31 * result + (extension?.hashCode() ?: 0)
        result = 31 * result + size.hashCode()
        result = 31 * result + (expiryTime?.hashCode() ?: 0)
        return result
    }
}