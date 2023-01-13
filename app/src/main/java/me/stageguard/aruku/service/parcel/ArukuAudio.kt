package me.stageguard.aruku.service.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import net.mamoe.mirai.message.data.Audio
import net.mamoe.mirai.message.data.AudioCodec
import net.mamoe.mirai.message.data.OfflineAudio

@Parcelize
data class ArukuAudio(
    val filename: String,
    val fileMd5: ByteArray,
    val fileSize: Long,
    val codec: Int,
    val extraData: ByteArray
) : Parcelable {
    fun toOfflineAudio(): OfflineAudio {
        return OfflineAudio(filename, fileMd5, fileSize, AudioCodec.fromId(codec), extraData)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ArukuAudio

        if (filename != other.filename) return false
        if (!fileMd5.contentEquals(other.fileMd5)) return false
        if (fileSize != other.fileSize) return false
        if (codec != other.codec) return false
        if (!extraData.contentEquals(other.extraData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + fileMd5.contentHashCode()
        result = 31 * result + fileSize.hashCode()
        result = 31 * result + codec
        result = 31 * result + extraData.contentHashCode()
        return result
    }
}

fun Audio.toArukuAudio(): ArukuAudio {
    return ArukuAudio(filename, fileMd5, fileSize, codec.id, extraData ?: ByteArray(0))
}