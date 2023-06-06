package me.stageguard.aruku.ui.page.login

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import me.stageguard.aruku.common.service.parcel.AccountLoginData
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.ui.UiState

class LoginViewModel(
    private val repository: MainRepository
) : ViewModel() {
    @UiState
    val accountInfo: MutableState<AccountLoginData> by lazy {
        mutableStateOf(
            AccountLoginData(
                accountNo = 0L,
                passwordMd5 = "",
                protocol = "ANDROID_PHONE",
                heartbeatStrategy = "STAT_HB",
                heartbeatPeriodMillis = 60 * 1000,
                heartbeatTimeoutMillis = 5 * 1000,
                statHeartbeatPeriodMillis = 300 * 1000,
                autoReconnect = true,
                reconnectionRetryTimes = 5
            )
        )
    }

    val protocols = listOf("ANDROID_PHONE", "ANDROID_PAD", "ANDROID_WATCH", "IPAD", "MACOS")
    val heartbeatStrategies = listOf("STAT_HB", "REGISTER", "NONE")
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