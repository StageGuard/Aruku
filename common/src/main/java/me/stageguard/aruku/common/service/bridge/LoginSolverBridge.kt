package me.stageguard.aruku.common.service.bridge

import remoter.annotations.ParamOut
import remoter.annotations.Remoter

@Remoter
interface LoginSolverBridge {
    fun onSolvePicCaptcha(bot: Long, @ParamOut data: ByteArray?): String?
    fun onSolveSliderCaptcha(bot: Long, url: String?): String?
    fun onSolveUnsafeDeviceLoginVerify(bot: Long, url: String?): String?
    fun onSolveSMSRequest(bot: Long, phone: String?): String?
}