package me.stageguard.aruku.service.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.stageguard.aruku.ui.page.ChatPageNav


@Parcelize
@Serializable
data class ContactId(
    val type: ContactType,
    val subject: Long,
) : Parcelable {
    fun toNavArg(messageId: Long? = null): String {
        return Json.encodeToString(ChatPageNav(this, messageId))
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + subject.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ContactId) return false
        return other.hashCode() == hashCode()
    }
}

@Parcelize
enum class ContactType : Parcelable {
    FRIEND, GROUP, TEMP
}