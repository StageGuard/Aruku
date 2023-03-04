package me.stageguard.aruku.service.bridge

import remoter.annotations.ParamOut
import remoter.annotations.Remoter

@Remoter
interface AccountStateBridge {
    fun onLogging(bot: Long)
    fun onSolvePicCaptcha(bot: Long, @ParamOut data: ByteArray?): String?
    fun onSolveSliderCaptcha(bot: Long, url: String?): String?
    fun onSolveUnsafeDeviceLoginVerify(bot: Long, url: String?): String?
    fun onSolveSMSRequest(bot: Long, phone: String?): String?
    fun onLoginSuccess(bot: Long)
    fun onLoginFailed(bot: Long, botKilled: Boolean, cause: String?)
    fun onOffline(bot: Long, cause: String, message: String?)

    object OfflineCause {
        const val SUBJECTIVE = "SUBJECTIVE"
        const val FORCE = "FORCE"
        const val DISCONNECTED = "DISCONNECTED"
    }
}