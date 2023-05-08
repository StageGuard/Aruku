package me.stageguard.aruku.service.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize



// remoter bug
sealed class AccountInfo(
    open val accountNo: Long,
    open val nickname: String,
    open val avatarUrl: String,
)

@Parcelize
class AccountInfoImpl(
    override val accountNo: Long,
    override val nickname: String,
    override val avatarUrl: String,
) : AccountInfo(accountNo, nickname, avatarUrl), Parcelable

@Parcelize
data class AccountProfile(
    val accountNo: Long,
    val nickname: String,
    val avatarUrl: String,
    val age: Int,
    val email: String,
    val qLevel: Int,
    val sign: String,
    val sex: Int,
) : Parcelable