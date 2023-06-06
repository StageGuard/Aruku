package me.stageguard.aruku.mirai_core

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeout
import me.stageguard.aruku.common.createAndroidLogger
import me.stageguard.aruku.common.service.parcel.AccountState
import me.stageguard.aruku.common.service.parcel.AccountState.CaptchaType
import me.stageguard.aruku.common.service.parcel.AccountState.QRCodeState
import net.mamoe.mirai.Bot
import net.mamoe.mirai.auth.QRCodeLoginListener
import net.mamoe.mirai.utils.DeviceVerificationRequests
import net.mamoe.mirai.utils.DeviceVerificationResult
import net.mamoe.mirai.utils.LoginSolver

class ArukuLoginSolver(
    private val stateProducer: SendChannel<AccountState>,
    private val solutionConsumer: ReceiveChannel<Solution>,
    private val solutionTimeout: Long = 60 * 1000L,
) : LoginSolver() {
    private val logger = createAndroidLogger()

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

    override fun createQRCodeLoginListener(bot: Bot) = object : QRCodeLoginListener {
        private var data: ByteArray? = null
        override fun onFetchQRCode(bot: Bot, data: ByteArray) {
            this.data = data
            if (!stateProducer.trySend(AccountState.QRCode(bot.id, data, QRCodeState.DEFAULT)).isSuccess) {
                logger.w("fail to send fetch qrcode state.")
            }
        }

        override fun onStateChanged(bot: Bot, state: QRCodeLoginListener.State) {
            if (data == null) logger.w("qrcode data is null while sending qrcode query state.")
            if (!stateProducer.trySend(AccountState.QRCode(bot.id, this.data!!, state.map())).isSuccess) {
                logger.w("fail to send query qrcode state.")
            }
        }

        private fun QRCodeLoginListener.State.map() = when (this) {
            QRCodeLoginListener.State.WAITING_FOR_SCAN -> QRCodeState.WAITING_FOR_SCAN
            QRCodeLoginListener.State.WAITING_FOR_CONFIRM -> QRCodeState.WAITING_FOR_CONFIRM
            QRCodeLoginListener.State.CANCELLED -> QRCodeState.CANCELLED
            QRCodeLoginListener.State.TIMEOUT -> QRCodeState.TIMEOUT
            QRCodeLoginListener.State.CONFIRMED -> QRCodeState.CONFIRMED
            QRCodeLoginListener.State.DEFAULT -> QRCodeState.DEFAULT
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