package me.stageguard.aruku.ui.page.login

import android.graphics.BitmapFactory
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.stageguard.aruku.service.IArukuMiraiInterface
import me.stageguard.aruku.service.ILoginSolver
import me.stageguard.aruku.service.parcel.AccountInfo
import net.mamoe.mirai.utils.BotConfiguration
import net.mamoe.mirai.utils.secondsToMillis

class LoginViewModel(
    private val arukuServiceInterface: IArukuMiraiInterface
) : ViewModel() {
    val accountInfo: MutableState<AccountInfo> by lazy {
        mutableStateOf(
            AccountInfo(
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

    private val _state: MutableStateFlow<LoginState> = MutableStateFlow(LoginState.Default)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private val captchaChannel = Channel<String?>()

    private val loginSolver = object : ILoginSolver.Stub() {
        override fun onSolvePicCaptcha(bot: Long, data: ByteArray?): String? {
            viewModelScope.updateState(
                if (data == null) {
                    LoginState.Failed(bot, "Picture captcha data is null.")
                } else {
                    LoginState.CaptchaRequired(
                        bot, CaptchaType.Picture(
                            bot,
                            BitmapFactory.decodeByteArray(data, 0, data.size).asImageBitmap()
                        )
                    )
                }
            )
            return runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
        }

        override fun onSolveSliderCaptcha(bot: Long, url: String?): String? {
            viewModelScope.updateState(
                if (url == null) {
                    LoginState.Failed(bot, "Slider captcha url is null.")
                } else {
                    LoginState.CaptchaRequired(bot, CaptchaType.Slider(bot, url))
                }
            )
            return runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
        }

        override fun onSolveUnsafeDeviceLoginVerify(bot: Long, url: String?): String? {
            viewModelScope.updateState(
                if (url == null) {
                    LoginState.Failed(bot, "UnsafeDeviceLogin captcha url is null.")
                } else {
                    LoginState.CaptchaRequired(bot, CaptchaType.UnsafeDevice(bot, url))
                }
            )
            return runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
        }

        override fun onSolveSMSRequest(bot: Long, phone: String?): String? {
            viewModelScope.updateState(LoginState.CaptchaRequired(bot, CaptchaType.SMSRequest(bot, phone)))
            return runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
        }

        override fun onLoginSuccess(bot: Long) {
            viewModelScope.updateState(LoginState.Success(bot))
        }

        override fun onLoginFailed(bot: Long, botKilled: Boolean, cause: String?) {
            viewModelScope.updateState(LoginState.Failed(bot, cause.toString()))
        }

    }

    fun doLogin(accountNo: Long) {
        viewModelScope.updateState(LoginState.Logging)
        arukuServiceInterface.addLoginSolver(accountNo, loginSolver)
        arukuServiceInterface.addBot(accountInfo.value, true)
    }

    fun retryCaptcha() {
        viewModelScope.updateState(LoginState.Logging)
        captchaChannel.trySend(null)
    }

    fun submitCaptcha(result: String) {
        viewModelScope.updateState(LoginState.Logging)
        captchaChannel.trySend(result)
    }

    fun removeBotAndClearState(accountNo: Long) {
        viewModelScope.updateState(LoginState.Default)
        arukuServiceInterface.removeBot(accountNo)
    }

    private fun CoroutineScope.updateState(s: LoginState) {
        launch {
            _state.emit(s)
        }
    }
}

sealed class LoginState {
    object Default : LoginState()
    object Logging : LoginState()
    class CaptchaRequired(val bot: Long, val type: CaptchaType) : LoginState()
    class Failed(val bot: Long, val cause: String) : LoginState()
    class Success(val bot: Long) : LoginState()
}

sealed class CaptchaType(val bot: Long) {
    class Picture(bot: Long, val imageBitmap: ImageBitmap) : CaptchaType(bot)
    class Slider(bot: Long, val url: String) : CaptchaType(bot)
    class UnsafeDevice(bot: Long, val url: String) : CaptchaType(bot)
    class SMSRequest(bot: Long, val phone: String?) : CaptchaType(bot)
}