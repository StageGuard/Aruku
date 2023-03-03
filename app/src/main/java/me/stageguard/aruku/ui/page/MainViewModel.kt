package me.stageguard.aruku.ui.page

import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heyanle.okkv2.core.Okkv
import com.heyanle.okkv2.core.okkv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.stageguard.aruku.ArukuApplication
import me.stageguard.aruku.R
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.service.bridge.LoginSolverBridge
import me.stageguard.aruku.ui.page.home.AccountState
import me.stageguard.aruku.ui.page.login.CaptchaType
import me.stageguard.aruku.ui.page.login.LoginState
import me.stageguard.aruku.util.stringRes
import me.stageguard.aruku.util.tag
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.ListeningStatus
import net.mamoe.mirai.event.events.BotOfflineEvent

class MainViewModel(
    private val repository: MainRepository,
    private val okkv: Okkv,
) : ViewModel() {
    val activeAccountPref = okkv.okkv<Long>("pref_active_bot")

    // represents state of current account
    // it is only changed at [observeAccountState] and [loginSolver]
    private val _accountState: MutableStateFlow<AccountState> =
        MutableStateFlow(AccountState.Default)
    val accountState: StateFlow<AccountState> get() = _accountState.asStateFlow()

    private val captchaChannel = Channel<String?>()
    private val loginSolver = object : LoginSolverBridge {
        override fun onSolvePicCaptcha(bot: Long, data: ByteArray?): String? {
            updateLoginState(
                if (data == null) {
                    AccountState.Offline(bot, "Picture captcha data is null.")
                } else {
                    AccountState.Login(
                        bot,
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
            updateLoginState(AccountState.Login(bot, LoginState.Logging))
            return captchaResult
        }

        override fun onSolveSliderCaptcha(bot: Long, url: String?): String? {
            updateLoginState(
                if (url == null) {
                    AccountState.Offline(bot, "Slider captcha url is null.")
                } else {
                    AccountState.Login(
                        bot,
                        LoginState.CaptchaRequired(bot, CaptchaType.Slider(bot, url))
                    )
                }
            )

            val captchaResult =
                runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
            updateLoginState(AccountState.Login(bot, LoginState.Logging))
            return captchaResult
        }

        override fun onSolveUnsafeDeviceLoginVerify(bot: Long, url: String?): String? {
            updateLoginState(
                if (url == null) {
                    AccountState.Offline(bot, "UnsafeDeviceLogin captcha url is null.")
                } else {
                    AccountState.Login(
                        bot,
                        LoginState.CaptchaRequired(bot, CaptchaType.UnsafeDevice(bot, url))
                    )
                }
            )

            val captchaResult =
                runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
            updateLoginState(AccountState.Login(bot, LoginState.Logging))
            return captchaResult
        }

        override fun onSolveSMSRequest(bot: Long, phone: String?): String? {
            updateLoginState(
                AccountState.Login(
                    bot,
                    LoginState.CaptchaRequired(bot, CaptchaType.SMSRequest(bot, phone ?: "-"))
                )
            )

            val captchaResult =
                runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
            updateLoginState(AccountState.Login(bot, LoginState.Logging))
            return captchaResult
        }

        override fun onLoginSuccess(bot: Long) {
            updateLoginState(AccountState.Online(bot))
        }

        override fun onLoginFailed(bot: Long, botKilled: Boolean, cause: String?) {
            updateLoginState(
                AccountState.Login(
                    bot,
                    LoginState.Failed(bot, cause.toString())
                )
            )
            Toast.makeText(
                ArukuApplication.INSTANCE.applicationContext,
                R.string.login_failed_please_retry.stringRes(bot.toString()),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    private var activeAccountLoginSolver: Pair<Long, LoginSolverBridge>? = null

    // observe account state
    context(CoroutineScope) fun observeAccountState(account: Long?) {
        val bot = if (account != null) Bot.getInstanceOrNull(account) else null
        Log.i(tag(), "observeAccountState $bot")
        // no bot provided from LocalBot, maybe first launch login or no account.
        if (bot == null) {
            // first launch login
            if (account != null) { // state: Logging
                Log.i(tag(), "First launch and observing background account login state.")
                updateLoginState(AccountState.Login(account, LoginState.Logging))

                activeAccountLoginSolver?.let { repository.removeLoginSolver(it.first) }
                val currentLoginSolver = account to loginSolver
                repository.addLoginSolver(account, currentLoginSolver.second)
                activeAccountLoginSolver = currentLoginSolver
            }
        } else {
            // bot is provided.
            // if offline, it is either logout manually or appears offline.
            if (!bot.isOnline) {
                this@CoroutineScope.launch(Dispatchers.IO) {
                    val dbAccount = repository.getAccount(bot.id)
                    if (dbAccount == null) {
                        Log.w(this@MainViewModel.tag(), "no account info in database ${bot.id}.")
                        return@launch
                    }

                    if (!dbAccount.isOfflineManually) { // state: Logging
                        Log.i(tag(), "Bot is now logging.")
                        updateLoginState(AccountState.Login(bot.id, LoginState.Logging))

                        activeAccountLoginSolver?.let { repository.removeLoginSolver(it.first) }
                        val currSolver = bot.id to loginSolver
                        repository.addLoginSolver(currSolver.first, currSolver.second)
                        activeAccountLoginSolver = currSolver
                    } else { // state: Offline
                        updateLoginState(AccountState.Offline(bot.id, null))
                    }
                }
            } else { // state: Online
                Log.i(tag(), "Bot is online.")
                updateLoginState(AccountState.Online(bot.id))
                bot.eventChannel.parentScope(this@CoroutineScope).subscribe<BotOfflineEvent> {
                    if (!viewModelScope.isActive) return@subscribe ListeningStatus.STOPPED
                    updateLoginState(AccountState.Offline(it.bot.id, it.toString()))
                    return@subscribe ListeningStatus.LISTENING
                }
            }
        }
    }

    fun submitCaptcha(accountNo: Long, result: String? = null) {
        updateLoginState(AccountState.Login(accountNo, LoginState.Logging))
        captchaChannel.trySend(result)
    }

    fun cancelLogin(accountNo: Long) {
        updateLoginState(AccountState.Offline(accountNo, null))
        viewModelScope.launch { repository.setAccountOfflineManually(accountNo) }
        Toast.makeText(
            ArukuApplication.INSTANCE.applicationContext,
            R.string.login_failed_please_retry.stringRes(accountNo.toString()),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun updateLoginState(s: AccountState) {
        viewModelScope.launch { _accountState.emit(s) }
    }

    fun doLogout(id: Long) {
        repository.logout(id)
    }

    fun doLogin(id: Long) {
        repository.login(id)
    }
}