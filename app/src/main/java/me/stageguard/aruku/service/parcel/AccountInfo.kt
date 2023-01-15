package me.stageguard.aruku.service.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AccountInfo(
    val accountNo: Long,
    val nickname: String,
    val avatarUrl: String,
    /*val age: Int,
    val email: String,
    val qLevel: Int,
    val sign: String,
    val sex: Int,*/
) : Parcelable