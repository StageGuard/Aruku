package me.stageguard.aruku.common.service.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class GroupMemberInfo(
    val senderId: Long,
    val senderName: String,
    val senderAvatarUrl: String,
    // TODO: more group member info fields from [NormalMember]
) : Parcelable