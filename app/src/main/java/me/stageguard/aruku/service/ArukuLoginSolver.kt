package me.stageguard.aruku.service

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeout
import me.stageguard.aruku.service.ArukuMiraiService.AccountState
import me.stageguard.aruku.service.ArukuMiraiService.AccountState.CaptchaType
import net.mamoe.mirai.Bot
import net.mamoe.mirai.utils.DeviceVerificationRequests
import net.mamoe.mirai.utils.DeviceVerificationResult
import net.mamoe.mirai.utils.LoginSolver

class ArukuLoginSolver(
    private val stateProducer: SendChannel<AccountState>,
    private val solutionConsumer: ReceiveChannel<Solution>
) : LoginSolver() {
    private val solutionTimeout = 30000L

    private suspend inline fun <reified T : Solution> receiveSolution(account: Long): T {
        return solutionConsumer.receiveAsFlow()
            .filterIsInstance<T>()
            .filter { it.account == account }.first()
    }

    private fun createState(account: Long, type: CaptchaType, data: ByteArray) =
        AccountState.Captcha(account, type, data)

    override suspend fun onSolvePicCaptcha(bot: Bot, data: ByteArray): String? {
        stateProducer.send(createState(bot.id, CaptchaType.PIC, data))

        return withTimeout(solutionTimeout) {
            receiveSolution<Solution.PicCaptcha>(bot.id).code
        }
    }

    override suspend fun onSolveSliderCaptcha(bot: Bot, url: String): String? {

        stateProducer.send(createState(bot.id, CaptchaType.SLIDER, url.toByteArray()))


        return withTimeout(solutionTimeout) {
            receiveSolution<Solution.SliderCaptcha>(bot.id).token
        }
    }

    override suspend fun onSolveDeviceVerification(
        bot: Bot,
        requests: DeviceVerificationRequests
    ): DeviceVerificationResult {
        val sms = requests.sms
        return if (sms != null) {
            sms.requestSms()
            val data = (sms.countryCode + sms.phoneNumber).toByteArray()
            stateProducer.send(createState(bot.id, CaptchaType.SMS, data))
            sms.solved(withTimeout(30000) {
                receiveSolution<Solution.SMSResult>(bot.id).code ?: ""
            })
        } else {
            val fallback = requests.fallback!!
            stateProducer.send(createState(bot.id, CaptchaType.USF, fallback.url.toByteArray()))
            withTimeout(30000) { receiveSolution<Solution.USFResult>(bot.id) }
            fallback.solved()
        }
    }

    override val isSliderCaptchaSupported: Boolean
        get() = true

    sealed class Solution(val account: Long) {
        class PicCaptcha(account: Long, val code: String?) : Solution(account)
        class SliderCaptcha(account: Long, val token: String?) : Solution(account)
        class USFResult(account: Long, val result: String?) : Solution(account)
        class SMSResult(account: Long, val code: String?) : Solution(account)
    }
}