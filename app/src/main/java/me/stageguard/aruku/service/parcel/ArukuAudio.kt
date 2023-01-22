package me.stageguard.aruku.service.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import me.stageguard.aruku.domain.data.message.Audio

@Parcelize
data class ArukuAudio(
    val filename: String,
    val fileMd5: ByteArray,
    val fileSize: Long,
    val extension: String
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ArukuAudio

        if (filename != other.filename) return false
        if (!fileMd5.contentEquals(other.fileMd5)) return false
        if (fileSize != other.fileSize) return false
        if (extension != other.extension) return false

        return true
    }

    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + fileMd5.contentHashCode()
        result = 31 * result + fileSize.hashCode()
        result = 31 * result + extension.hashCode()
        return result
    }
}

fun Audio.toArukuAudio(): ArukuAudio {
    return ArukuAudio(fileName, fileMd5, fileSize, extension)
}