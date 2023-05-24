package me.stageguard.aruku.ui.page

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.viewModelScope
import com.heyanle.okkv2.core.Okkv
import com.heyanle.okkv2.core.okkv
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.service.bridge.LoginSolverBridge
import me.stageguard.aruku.service.parcel.AccountLoginData
import me.stageguard.aruku.service.parcel.AccountState
import me.stageguard.aruku.ui.UiState
import me.stageguard.aruku.ui.page.login.CaptchaType
import me.stageguard.aruku.ui.page.login.LoginState
import me.stageguard.aruku.util.createAndroidLogger

class MainViewModel(
    private val repository: MainRepository,
    private val okkv: Okkv,
    accountStateFlow: Flow<Map<Long, AccountState>>,
    composableLifecycleOwner: LifecycleOwner,
) : ViewModel() {
    private val logger = createAndroidLogger()

    @UiState
    val activeAccountPref = okkv.okkv<Long>("pref_active_bot")

    // first come-in and first consumes, so is not necessary to pass account no.
    private val captchaChannel = Channel<String?>()

    // TODO: maybe we should change _accountList to List<AccountState>
    // so we can control all account state in service,
    // and just receive at here
    @UiState
    val accountsState: Flow<Map<Long, UIAccountState>> =
        accountStateFlow
            .flowWithLifecycle(composableLifecycleOwner.lifecycle)
            .map { states -> states.map { (account, state) -> account to state.mapUiState() }.toMap() }
            .onEach {
                logger.i("AccountState: " + it.entries.joinToString { e -> "${e.key}: ${e.value}" })
            }.stateIn(viewModelScope, SharingStarted.Lazily, mapOf())

    init {
        repository.attachLoginSolver(object : LoginSolverBridge {
            override fun onSolvePicCaptcha(bot: Long, data: ByteArray?): String? {
                return runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
            }

            override fun onSolveSliderCaptcha(bot: Long, url: String?): String? {
                return runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
            }

            override fun onSolveUnsafeDeviceLoginVerify(bot: Long, url: String?): String? {
                return runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
            }

            override fun onSolveSMSRequest(bot: Long, phone: String?): String? {
                return runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
            }
        })
    }

    fun logout(account: Long) {
        repository.logout(account)
    }

    fun login(account: Long) {
        repository.login(account)
    }

    fun addBotAndLogin(info: AccountLoginData) {
        repository.addBot(info, true)
    }

    suspend fun submitCaptcha(result: String? = null) {
        captchaChannel.send(result)
    }

    fun removeBot(account: Long) {
        repository.removeBot(account)
    }
}

sealed interface UIAccountState {
    object Default : UIAccountState {
        override fun toString(): String {
            return "Default"
        }
    }

    class Login(val state: LoginState) : UIAccountState {
        override fun toString(): String {
            return "Login(state=$state)"
        }
    }

    object Online : UIAccountState {
        override fun toString(): String {
            return "Online"
        }
    }

    class Offline(val cause: String? = null, val message: String? = null) : UIAccountState {
        override fun toString(): String {
            return "Offline(cause=$cause)"
        }
    }
}

fun AccountState.mapUiState() = when (this) {
    is AccountState.Logging -> UIAccountState.Login(LoginState.Logging)
    is AccountState.Offline -> UIAccountState.Offline(cause.toString(), message)
    is AccountState.QRCode -> UIAccountState.Default // TODO
    is AccountState.Online -> UIAccountState.Online
    is AccountState.Captcha -> UIAccountState.Login(
        LoginState.CaptchaRequired(
            account, when (type) {
                AccountState.CaptchaType.SMS -> CaptchaType.SMSRequest(account, data.decodeToString())
                AccountState.CaptchaType.PIC -> CaptchaType.Picture(account, data)
                AccountState.CaptchaType.SLIDER -> CaptchaType.Slider(account, data.decodeToString())
                AccountState.CaptchaType.USF -> CaptchaType.UnsafeDevice(account, data.decodeToString())
            }
        )
    )
}