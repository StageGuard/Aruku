package me.stageguard.aruku.service

import net.mamoe.mirai.Bot
import net.mamoe.mirai.network.CustomLoginFailedException
import net.mamoe.mirai.utils.DeviceVerificationRequests
import net.mamoe.mirai.utils.DeviceVerificationResult
import net.mamoe.mirai.utils.LoginSolver
import java.lang.ref.WeakReference

class ArukuLoginSolver(private val delegate: WeakReference<Map<Long, ILoginSolver>>) : LoginSolver() {
    override suspend fun onSolvePicCaptcha(bot: Bot, data: ByteArray): String {
        return delegate.get()?.get(bot.id)?.onSolvePicCaptcha(bot.id, data) ?: throw object :
            CustomLoginFailedException(false, "no login solver for bot ${bot.id}") {}
    }

    override suspend fun onSolveSliderCaptcha(bot: Bot, url: String): String {
        return delegate.get()?.get(bot.id)?.onSolveSliderCaptcha(bot.id, url) ?: throw object :
            CustomLoginFailedException(false, "no login solver for bot ${bot.id}") {}
    }

    @Deprecated(
        "Please use onSolveDeviceVerification instead",
        replaceWith = ReplaceWith("onSolveDeviceVerification(bot, url, null)"),
        level = DeprecationLevel.WARNING
    )
    override suspend fun onSolveUnsafeDeviceLoginVerify(bot: Bot, url: String): String {
        return delegate.get()?.get(bot.id)?.onSolveUnsafeDeviceLoginVerify(bot.id, url) ?: throw object :
            CustomLoginFailedException(false, "no login solver for bot ${bot.id}") {}
    }

    override suspend fun onSolveDeviceVerification(
        bot: Bot,
        requests: DeviceVerificationRequests
    ): DeviceVerificationResult {
        val sms = requests.sms
        return if (sms != null) {
            sms.requestSms()
            val result = delegate.get()?.get(bot.id)?.onSolveSMSRequest(bot.id, sms.countryCode + sms.phoneNumber)
                ?: throw object : CustomLoginFailedException(false, "no login solver for bot ${bot.id}") {}
            sms.solved(result)
        } else {
            val fallback = requests.fallback!!
            delegate.get()?.get(bot.id)?.onSolveUnsafeDeviceLoginVerify(bot.id, fallback.url)
                ?: throw object : CustomLoginFailedException(false, "no login solver for bot ${bot.id}") {}
            fallback.solved()
        }
    }

    override val isSliderCaptchaSupported: Boolean
        get() = true
}