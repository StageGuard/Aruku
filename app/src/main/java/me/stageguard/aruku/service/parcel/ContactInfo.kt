package me.stageguard.aruku.service.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import net.mamoe.mirai.contact.Friend
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.nameCardOrNick

@Parcelize
@Serializable
data class ContactInfo(
    val id: ContactId,
    val name: String? = null,
    val avatarUrl: String? = null,
) : Parcelable

fun Group.toContactInfo() = ContactInfo(
    id = ContactId(
        type = ContactType.GROUP,
        subject = this.id
    ),
    name = this.name,
    avatarUrl = this.avatarUrl
)

fun Friend.toContactInfo() = ContactInfo(
    id = ContactId(
        type = ContactType.FRIEND,
        subject = this.id
    ),
    name = this.nameCardOrNick,
    avatarUrl = this.avatarUrl
)