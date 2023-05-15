package me.stageguard.aruku.service.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import net.mamoe.mirai.contact.Member
import net.mamoe.mirai.contact.remarkOrNameCardOrNick

@Parcelize
data class GroupMemberInfo(
    val senderId: Long,
    val senderName: String,
    val senderAvatarUrl: String,
    // TODO: more group member info fields from [NormalMember]
) : Parcelable

fun Member.toGroupMemberInfo(): GroupMemberInfo {
    return GroupMemberInfo(
        senderId = id,
        senderName = remarkOrNameCardOrNick,
        senderAvatarUrl = avatarUrl
    )
}