package me.stageguard.aruku.ui.page.login

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.service.parcel.AccountLoginData
import me.stageguard.aruku.ui.UiState
import net.mamoe.mirai.utils.BotConfiguration
import net.mamoe.mirai.utils.secondsToMillis

class LoginViewModel(
    private val repository: MainRepository
) : ViewModel() {
    @UiState
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
    object Default : LoginState {
        override fun toString(): String {
            return "Default"
        }
    }
    object Logging : LoginState {
        override fun toString(): String {
            return "Logging"
        }
    }
    class CaptchaRequired(val bot: Long, val type: CaptchaType) : LoginState {
        override fun toString(): String {
            return "CaptchaRequired(bot=$bot, type=$type)"
        }
    }
    class Failed(val bot: Long, val cause: String) : LoginState {
        override fun toString(): String {
            return "Failed($bot=bot, cause=$cause)"
        }
    }
    class Success(val bot: Long) : LoginState {
        override fun toString(): String {
            return "Success(bot=$bot)"
        }
    }
}

sealed class CaptchaType(val bot: Long) {
    class Picture(bot: Long, val image: ByteArray) : CaptchaType(bot)
    class Slider(bot: Long, val url: String) : CaptchaType(bot)
    class UnsafeDevice(bot: Long, val url: String) : CaptchaType(bot)
    class SMSRequest(bot: Long, val phone: String?) : CaptchaType(bot)
}