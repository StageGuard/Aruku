package me.stageguard.aruku.ui.page

import android.widget.Toast
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.stageguard.aruku.ArukuApplication
import me.stageguard.aruku.R
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.service.bridge.LoginSolverBridge
import me.stageguard.aruku.service.parcel.AccountLoginData
import me.stageguard.aruku.service.parcel.AccountState
import me.stageguard.aruku.ui.UiState
import me.stageguard.aruku.ui.page.login.CaptchaType
import me.stageguard.aruku.ui.page.login.LoginState
import me.stageguard.aruku.util.createAndroidLogger
import me.stageguard.aruku.util.stringRes

class MainViewModel(
    private val repository: MainRepository,
    private val okkv: Okkv,
    serviceAccountStateFlow: Flow<Map<Long, AccountState>>,
    composableLifecycleOwner: LifecycleOwner,
) : ViewModel() {
    private val logger = createAndroidLogger("MainViewModel")

    @UiState
    val activeAccountPref = okkv.okkv<Long>("pref_active_bot")

    private val additionalStateProducer = Channel<Pair<Long, UIAccountState>>()

    // first come-in and first consumes, so is not necessary to pass account no.
    private val captchaChannel = Channel<String?>()
    private val loginSolver = object : LoginSolverBridge {
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
    }

    // TODO: maybe we should change _accountList to List<AccountState>
    // so we can control all account state in service,
    // and just receive at here
    @UiState
    val accountsState: Flow<Map<Long, UIAccountState>> =
        serviceAccountStateFlow
            .flowWithLifecycle(composableLifecycleOwner.lifecycle)
            .map { states -> states.map { (account, state) -> account to state.mapUiState() }.toMap() }
            .onEach {
                logger.i("AccountState: " + it.entries.joinToString { e -> "${e.key}: ${e.value}" })
            }.stateIn(viewModelScope, SharingStarted.Lazily, mapOf())

    fun submitCaptcha(accountNo: Long, result: String? = null) {
        captchaChannel.trySend(result)
    }

    fun cancelLogin(accountNo: Long) {
        viewModelScope.launch {
            additionalStateProducer.send(accountNo to UIAccountState.Offline("offline manually"))
            repository.setAccountOfflineManually(accountNo)
        }
        Toast.makeText(
            ArukuApplication.INSTANCE.applicationContext,
            R.string.login_failed_please_retry.stringRes(accountNo.toString()),
            Toast.LENGTH_SHORT
        ).show()
    }

    fun doLogout(account: Long) {
        repository.logout(account)
    }

    fun doLogin(account: Long) {
        repository.login(account)
    }

    fun doLogin(account: Long, info: AccountLoginData) {
        repository.addBot(info, true)
    }

    fun retryCaptcha(account: Long) {
        captchaChannel.trySend(null)
    }

    fun removeAccount(accountNo: Long) {
        repository.removeBot(accountNo)
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

    class Offline(val cause: String? = null) : UIAccountState {
        override fun toString(): String {
            return "Offline(cause=$cause)"
        }
    }
}

fun AccountState.mapUiState() = when (this) {
    is AccountState.Logging -> UIAccountState.Login(LoginState.Logging)
    is AccountState.Offline -> UIAccountState.Offline(cause.toString())
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