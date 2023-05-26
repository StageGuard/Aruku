package me.stageguard.aruku.service.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class RemoteFile(
    val id: String,
    val url: String?,
    val name: String,
    val md5: ByteArray,
    val extension: String,
    val size: Long,
    val expiryTime: Long,
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RemoteFile

        if (url != other.url) return false
        if (name != other.name) return false
        if (!md5.contentEquals(other.md5)) return false
        if (extension != other.extension) return false
        if (size != other.size) return false
        return expiryTime == other.expiryTime
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + md5.contentHashCode()
        result = 31 * result + extension.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + expiryTime.hashCode()
        return result
    }
}