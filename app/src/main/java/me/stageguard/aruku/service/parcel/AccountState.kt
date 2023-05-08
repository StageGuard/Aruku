package me.stageguard.aruku.service.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed class AccountState private constructor(open val account: Long) {
    @Parcelize
    class Logging(override val account: Long) : AccountState(account), Parcelable

    @Parcelize
    class Online(override val account: Long) : AccountState(account), Parcelable

    @Parcelize
    class Offline(override val account: Long, val cause: OfflineCause, val message: String?) :
        AccountState(account), Parcelable

    @Parcelize
    class Captcha(override val account: Long, val type: CaptchaType, val data: ByteArray) :
        AccountState(account), Parcelable

    @Parcelize
    class QRCode(override val account: Long, val data: ByteArray, val state: QRCodeState) :
        AccountState(account), Parcelable

    @Parcelize
    enum class CaptchaType : Parcelable { PIC, SLIDER, USF, SMS }

    @Parcelize
    enum class OfflineCause : Parcelable { SUBJECTIVE, FORCE, DISCONNECTED, LOGIN_FAILED, REMOVE_BOT, INIT }

    @Parcelize
    enum class QRCodeState : Parcelable {
        WAITING_FOR_SCAN, WAITING_FOR_CONFIRM, CANCELLED, TIMEOUT, CONFIRMED, DEFAULT
    }
}