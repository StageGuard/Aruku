package me.stageguard.aruku.ui.page.login

import android.graphics.BitmapFactory
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import me.stageguard.aruku.preference.proto.AccountsOuterClass
import me.stageguard.aruku.service.IArukuMiraiInterface
import me.stageguard.aruku.service.ILoginSolver
import net.mamoe.mirai.utils.secondsToMillis
import me.stageguard.aruku.preference.proto.AccountsOuterClass.Accounts.AccountInfo as AccountInfoProto
import me.stageguard.aruku.service.parcel.AccountInfo as AccountInfoParcel

class LoginViewModel(
    private val arukuServiceInterface: IArukuMiraiInterface,
    private val onLoginSuccess: (AccountInfoProto) -> Unit
) : ViewModel() {
    val accountInfo: MutableState<AccountInfoProto> by lazy {
        mutableStateOf(AccountInfoProto.newBuilder().apply {
            protocol = AccountsOuterClass.Accounts.login_protocol.ANDROID_PHONE
            heartbeatStrategy = AccountsOuterClass.Accounts.heartbeat_strategy.STAT_HB
            heartbeatPeriodMillis = 60.secondsToMillis
            heartbeatTimeoutMillis = 5.secondsToMillis
            statHeartbeatPeriodMillis = 300.secondsToMillis
            autoReconnect = true
            reconnectionRetryTimes = 5
        }.build())
    }

    val state: MutableState<LoginState> = mutableStateOf(LoginState.Default)
    private val captchaChannel = Channel<String?>()

    private val loginSolver = object : ILoginSolver.Stub() {
        override fun onSolvePicCaptcha(bot: Long, data: ByteArray?): String? {
            state.value = if (data == null) {
                LoginState.LoginFailed(bot, "Picture captcha data is null.")
            } else {
                LoginState.CaptchaRequired(
                    bot, CaptchaType.Picture(
                        bot,
                        BitmapFactory.decodeByteArray(data, 0, data.size).asImageBitmap()
                    )
                )
            }
            return runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
        }

        override fun onSolveSliderCaptcha(bot: Long, url: String?): String? {
            state.value = if (url == null) {
                LoginState.LoginFailed(bot, "Slider captcha url is null.")
            } else {
                LoginState.CaptchaRequired(bot, CaptchaType.Slider(bot, url))
            }
            return runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
        }

        override fun onSolveUnsafeDeviceLoginVerify(bot: Long, url: String?): String? {
            state.value = if (url == null) {
                LoginState.LoginFailed(bot, "UnsafeDeviceLogin captcha url is null.")
            } else {
                LoginState.CaptchaRequired(bot, CaptchaType.UnsafeDevice(bot, url))
            }
            return runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
        }

        override fun onLoginSuccess(bot: Long) {
            runBlocking(Dispatchers.Main) { onLoginSuccess(accountInfo.value) }
        }

        override fun onLoginFailed(bot: Long, botKilled: Boolean, cause: String?) {
            state.value = LoginState.LoginFailed(bot, cause.toString())
        }

    }

    fun doLogin(accountNo: Long) {
        state.value = LoginState.Logging
        arukuServiceInterface.addLoginSolver(accountNo, loginSolver)
        arukuServiceInterface.addBot(
            AccountInfoParcel(
                accountNo = accountInfo.value.accountNo,
                passwordMd5 = accountInfo.value.passwordMd5,
                protocol = accountInfo.value.protocol,
                heartbeatStrategy = accountInfo.value.heartbeatStrategy,
                heartbeatPeriodMillis = accountInfo.value.heartbeatPeriodMillis,
                heartbeatTimeoutMillis = accountInfo.value.heartbeatTimeoutMillis,
                statHeartbeatPeriodMillis = accountInfo.value.statHeartbeatPeriodMillis,
                autoReconnect = accountInfo.value.autoReconnect,
                reconnectionRetryTimes = accountInfo.value.reconnectionRetryTimes
            ), true
        )
    }

    fun retryCaptcha() {
        state.value = LoginState.Logging
        captchaChannel.trySend(null)
    }

    fun submitCaptcha(result: String) {
        state.value = LoginState.Logging
        captchaChannel.trySend(result)
    }

    fun removeBotAndClearState(accountNo: Long) {
        state.value = LoginState.Default
        arukuServiceInterface.removeBot(accountNo)
    }
}

sealed class LoginState {
    object Default : LoginState()
    object Logging : LoginState()
    class CaptchaRequired(val bot: Long, val type: CaptchaType) : LoginState()
    class LoginFailed(val bot: Long, val cause: String) : LoginState()
}

sealed class CaptchaType(val bot: Long) {
    class Picture(bot: Long, val imageBitmap: ImageBitmap) : CaptchaType(bot)
    class Slider(bot: Long, val url: String) : CaptchaType(bot)
    class UnsafeDevice(bot: Long, val url: String) : CaptchaType(bot)
}