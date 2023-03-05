package me.stageguard.aruku.ui.page

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heyanle.okkv2.core.Okkv
import com.heyanle.okkv2.core.okkv
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.stageguard.aruku.ArukuApplication
import me.stageguard.aruku.R
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.service.bridge.AccountStateBridge
import me.stageguard.aruku.service.parcel.AccountLoginData
import me.stageguard.aruku.ui.page.login.CaptchaType
import me.stageguard.aruku.ui.page.login.LoginState
import me.stageguard.aruku.util.stringRes

class MainViewModel(
    private val repository: MainRepository,
    private val okkv: Okkv,
    @Suppress("LocalVariableName") _accountList: LiveData<List<Long>>,
    composableLifecycleOwner: LifecycleOwner,
) : ViewModel() {
    val activeAccountPref = okkv.okkv<Long>("pref_active_bot")

    private val accountStateProducer = Channel<Pair<Long, AccountState>>()

    // first come-in and first consumes, so is not necessary to pass account no.
    private val captchaChannel = Channel<String?>()
    private val stateBridge = object : AccountStateBridge {
        override fun onLogging(bot: Long) {
            updateAccountState(bot, AccountState.Login(LoginState.Logging))
        }

        override fun onSolvePicCaptcha(bot: Long, data: ByteArray?): String? {
            updateAccountState(
                bot,
                if (data == null) {
                    AccountState.Offline("Picture captcha data is null.")
                } else {
                    AccountState.Login(
                        LoginState.CaptchaRequired(
                            bot, CaptchaType.Picture(
                                bot,
                                BitmapFactory.decodeByteArray(data, 0, data.size).asImageBitmap()
                            )
                        )
                    )
                }
            )
            val captchaResult =
                runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
            updateAccountState(bot, AccountState.Login(LoginState.Logging))
            return captchaResult
        }

        override fun onSolveSliderCaptcha(bot: Long, url: String?): String? {
            updateAccountState(
                bot,
                if (url == null) {
                    AccountState.Offline("Slider captcha url is null.")
                } else {
                    AccountState.Login(
                        LoginState.CaptchaRequired(bot, CaptchaType.Slider(bot, url))
                    )
                }
            )

            val captchaResult =
                runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
            updateAccountState(bot, AccountState.Login(LoginState.Logging))
            return captchaResult
        }

        override fun onSolveUnsafeDeviceLoginVerify(bot: Long, url: String?): String? {
            updateAccountState(
                bot,
                if (url == null) {
                    AccountState.Offline("UnsafeDeviceLogin captcha url is null.")
                } else {
                    AccountState.Login(
                        LoginState.CaptchaRequired(bot, CaptchaType.UnsafeDevice(bot, url))
                    )
                }
            )

            val captchaResult =
                runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
            updateAccountState(bot, AccountState.Login(LoginState.Logging))
            return captchaResult
        }

        override fun onSolveSMSRequest(bot: Long, phone: String?): String? {
            updateAccountState(
                bot,
                AccountState.Login(
                    LoginState.CaptchaRequired(bot, CaptchaType.SMSRequest(bot, phone ?: "-"))
                )
            )

            val captchaResult =
                runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
            updateAccountState(bot, AccountState.Login(LoginState.Logging))
            return captchaResult
        }

        override fun onLoginSuccess(bot: Long) {
            updateAccountState(bot, AccountState.Login(LoginState.Success(bot)))
            updateAccountState(bot, AccountState.Online)
        }

        override fun onLoginFailed(bot: Long, botKilled: Boolean, cause: String?) {
            updateAccountState(bot, AccountState.Login(LoginState.Failed(bot, "$cause")))
            Toast.makeText(
                ArukuApplication.INSTANCE.applicationContext,
                R.string.login_failed_please_retry.stringRes(bot.toString()),
                Toast.LENGTH_SHORT
            ).show()
        }

        override fun onOffline(
            bot: Long,
            cause: String,
            message: String?
        ) {
            updateAccountState(bot, AccountState.Offline(cause))
        }
    }

    // TODO: maybe we should change _accountList to List<AccountState>
    // so we can control all account state in service,
    // and just receive at here
    val accountsState: Flow<Map<Long, AccountState>> = channelFlow {
        val list = mutableListOf<Pair<Long, AccountState>>()

        fun getAccountOnlineState(account: Long): AccountState {
            return when (repository.getAccountOnlineState(account)) {
                true -> AccountState.Online
                false -> AccountState.Offline()
                null -> AccountState.Default
            }
        }

        // first receive state
        _accountList.value?.map { it to getAccountOnlineState(it) }?.let(list::addAll)

        // list update state
        _accountList.observe(composableLifecycleOwner) { bots ->
            if (list.isEmpty()) {
                list.addAll(bots.map { it to getAccountOnlineState(it) })
                trySend(list.toMap())
                return@observe
            }

            list.removeIf { (bot, _) -> bot !in bots }
            bots.forEach { b ->
                if (list.find { it.first == b } == null) {
                    list.add(b to getAccountOnlineState(b))
                }
            }
            trySend(list.toMap())
        }

        // state bridge
        accountStateProducer.consumeAsFlow().collect { p ->
            list.removeIf { it.first == p.first }
            list.add(p)
            send(list.toMap())
        }
    }

    init {
        repository.setAccountStateBridge(stateBridge)
    }

    fun submitCaptcha(accountNo: Long, result: String? = null) {
        updateAccountState(accountNo, AccountState.Login(LoginState.Logging))
        captchaChannel.trySend(result)
    }

    fun cancelLogin(accountNo: Long) {
        updateAccountState(accountNo, AccountState.Offline("cancelled manually"))
        viewModelScope.launch { repository.setAccountOfflineManually(accountNo) }
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
        updateAccountState(account, AccountState.Login(LoginState.Logging))
        repository.login(account)
    }

    fun doLogin(account: Long, info: AccountLoginData) {
        updateAccountState(account, AccountState.Login(LoginState.Logging))
        repository.addBot(info, true)
    }

    fun retryCaptcha(account: Long) {
        updateAccountState(account, AccountState.Login(LoginState.Logging))
        captchaChannel.trySend(null)
    }

    fun removeAccount(accountNo: Long) {
        repository.removeBot(accountNo)
    }

    private fun updateAccountState(account: Long, state: AccountState) {
        viewModelScope.launch { accountStateProducer.send(account to state) }
    }
}

sealed interface AccountState {
    object Default : AccountState
    class Login(val state: LoginState) : AccountState
    object Online : AccountState
    class Offline(val cause: String? = null) : AccountState
}