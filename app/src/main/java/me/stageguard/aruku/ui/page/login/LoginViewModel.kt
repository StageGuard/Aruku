package me.stageguard.aruku.ui.page.login

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.service.parcel.AccountLoginData
import net.mamoe.mirai.utils.BotConfiguration
import net.mamoe.mirai.utils.secondsToMillis

class LoginViewModel(
    private val repository: MainRepository
) : ViewModel() {
    val accountInfo: MutableState<AccountLoginData> by lazy {
        mutableStateOf(
            AccountLoginData(
                accountNo = 0L,
                passwordMd5 = "",
                protocol = BotConfiguration.MiraiProtocol.ANDROID_PHONE.toString(),
                heartbeatStrategy = BotConfiguration.HeartbeatStrategy.STAT_HB.toString(),
                heartbeatPeriodMillis = 60.secondsToMillis,
                heartbeatTimeoutMillis = 5.secondsToMillis,
                statHeartbeatPeriodMillis = 300.secondsToMillis,
                autoReconnect = true,
                reconnectionRetryTimes = 5
            )
        )
    }
}

sealed interface LoginState {
    object Default : LoginState
    object Logging : LoginState
    class CaptchaRequired(val bot: Long, val type: CaptchaType) : LoginState
    class Failed(val bot: Long, val cause: String) : LoginState
    class Success(val bot: Long) : LoginState
}

sealed class CaptchaType(val bot: Long) {
    class Picture(bot: Long, val imageBitmap: ImageBitmap) : CaptchaType(bot)
    class Slider(bot: Long, val url: String) : CaptchaType(bot)
    class UnsafeDevice(bot: Long, val url: String) : CaptchaType(bot)
    class SMSRequest(bot: Long, val phone: String?) : CaptchaType(bot)
}