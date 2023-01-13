package me.stageguard.aruku.service.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class GroupAtMessage(
    val id: Long,
    val display: String,
) : Parcelable