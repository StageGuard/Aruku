package me.stageguard.aruku.common.service.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable


@Parcelize
@Serializable
data class ContactId(
    val type: ContactType,
    val subject: Long,
) : Parcelable {

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