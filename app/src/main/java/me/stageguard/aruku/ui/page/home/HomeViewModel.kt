package me.stageguard.aruku.ui.page.home

import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Message
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.stageguard.aruku.ArukuApplication
import me.stageguard.aruku.R
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.preference.ArukuPreference
import me.stageguard.aruku.service.bridge.LoginSolverBridge
import me.stageguard.aruku.ui.LocalNavController
import me.stageguard.aruku.ui.page.NAV_CHAT
import me.stageguard.aruku.ui.page.home.contact.HomeContactPage
import me.stageguard.aruku.ui.page.home.message.HomeMessagePage
import me.stageguard.aruku.ui.page.home.profile.HomeProfilePage
import me.stageguard.aruku.ui.page.login.CaptchaType
import me.stageguard.aruku.ui.page.login.LoginState
import me.stageguard.aruku.util.stringRes
import me.stageguard.aruku.util.tag
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.ListeningStatus
import net.mamoe.mirai.event.events.BotOfflineEvent

class HomeViewModel(
    private val repository: MainRepository,
    private val _accountList: LiveData<List<Long>>,
) : ViewModel() {
    // represents state of current account
    // it is only changed at [observeAccountState] and [loginSolver]
    private val _loginState: MutableStateFlow<AccountState> = MutableStateFlow(AccountState.Default)
    val loginState: StateFlow<AccountState> get() = _loginState.asStateFlow()
    private val _accounts: MutableStateFlow<List<BasicAccountInfo>> by lazy {
        MutableStateFlow(
            (_accountList.value ?: repository.getBots())
                .mapNotNull { repository.queryAccountInfo(it) }
                .map { BasicAccountInfo(it.accountNo, it.nickname, it.avatarUrl) }
        )
    }
    private val accountListUpdateFlow = MutableStateFlow(0L)
    val accounts: Flow<List<BasicAccountInfo>> =
        _accounts.combine(accountListUpdateFlow) { info, _ -> info }

    fun observeAccountList(owner: LifecycleOwner) {
        _accountList.observe(owner) { list ->
            _accounts.value = list
                .mapNotNull { repository.queryAccountInfo(it) }
                .map { BasicAccountInfo(it.accountNo, it.nickname, it.avatarUrl) }
        }
    }

    private val captchaChannel = Channel<String?>()
    private val loginSolver = object : LoginSolverBridge {
        override fun onSolvePicCaptcha(bot: Long, data: ByteArray?): String? {
            viewModelScope.updateLoginState(
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
            viewModelScope.updateLoginState(AccountState.Login(bot, LoginState.Logging))
            return captchaResult
        }

        override fun onSolveSliderCaptcha(bot: Long, url: String?): String? {
            viewModelScope.updateLoginState(
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
            viewModelScope.updateLoginState(AccountState.Login(bot, LoginState.Logging))
            return captchaResult
        }

        override fun onSolveUnsafeDeviceLoginVerify(bot: Long, url: String?): String? {
            viewModelScope.updateLoginState(
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
            viewModelScope.updateLoginState(AccountState.Login(bot, LoginState.Logging))
            return captchaResult
        }

        override fun onSolveSMSRequest(bot: Long, phone: String?): String? {
            viewModelScope.updateLoginState(
                AccountState.Login(
                    bot,
                    LoginState.CaptchaRequired(bot, CaptchaType.SMSRequest(bot, phone ?: "-"))
                )
            )

            val captchaResult =
                runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
            viewModelScope.updateLoginState(AccountState.Login(bot, LoginState.Logging))
            return captchaResult
        }

        override fun onLoginSuccess(bot: Long) {
            viewModelScope.updateLoginState(AccountState.Online(bot))
            accountListUpdateFlow.value = System.currentTimeMillis()
        }

        override fun onLoginFailed(bot: Long, botKilled: Boolean, cause: String?) {
            viewModelScope.updateLoginState(
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

    val currentNavSelection = mutableStateOf(homeNaves[HomeNavSelection.MESSAGE]!!)

    // observe account state
    context(CoroutineScope) fun observeAccountState(account: Long?) {
        val bot = if (account != null) Bot.getInstanceOrNull(account) else null
        Log.i(tag(), "observeAccountState $bot")
        // no bot provided from LocalBot, maybe first launch login or no account.
        if (bot == null) {
            val activeBot = ArukuPreference.activeBot
            // first launch login
            if (activeBot != null) { // state: Logging
                Log.i(tag(), "First launch and observing background account login state.")
                viewModelScope.updateLoginState(AccountState.Login(activeBot, LoginState.Logging))

                activeAccountLoginSolver?.let { repository.removeLoginSolver(it.first) }
                val currentLoginSolver = activeBot to loginSolver
                repository.addLoginSolver(activeBot, currentLoginSolver.second)
                activeAccountLoginSolver = currentLoginSolver
            }
        } else {
            // bot is provided.
            // if offline, it is either logout manually or appears offline.
            if (!bot.isOnline) {
                this@CoroutineScope.launch(Dispatchers.IO) {
                    val dbAccount = repository.getAccount(bot.id)
                    if (dbAccount == null) {
                        Log.w(
                            tag(),
                            "LocalBot is provided but the account is not found in database."
                        )
                        return@launch
                    }

                    if (!dbAccount.isOfflineManually) { // state: Logging
                        Log.i(tag(), "Bot is now logging.")
                        viewModelScope.updateLoginState(
                            AccountState.Login(
                                bot.id,
                                LoginState.Logging
                            )
                        )

                        activeAccountLoginSolver?.let { repository.removeLoginSolver(it.first) }
                        val currSolver = bot.id to loginSolver
                        repository.addLoginSolver(currSolver.first, currSolver.second)
                        activeAccountLoginSolver = currSolver
                    } else { // state: Offline
                        viewModelScope.updateLoginState(AccountState.Offline(bot.id, null))
                    }
                }
            } else { // state: Online
                Log.i(tag(), "Bot is online.")
                viewModelScope.updateLoginState(AccountState.Online(bot.id))
                bot.eventChannel.parentScope(this@CoroutineScope).subscribe<BotOfflineEvent> {
                    if (!viewModelScope.isActive) return@subscribe ListeningStatus.STOPPED
                    viewModelScope.updateLoginState(AccountState.Offline(it.bot.id, it.toString()))
                    return@subscribe ListeningStatus.LISTENING
                }
            }
        }
    }

    fun submitCaptcha(accountNo: Long, result: String? = null) {
        viewModelScope.updateLoginState(AccountState.Login(accountNo, LoginState.Logging))
        captchaChannel.trySend(result)
    }

    fun cancelLogin(accountNo: Long) {
        viewModelScope.updateLoginState(AccountState.Offline(accountNo, null))
        viewModelScope.launch { repository.setAccountOfflineManually(accountNo) }
        Toast.makeText(
            ArukuApplication.INSTANCE.applicationContext,
            R.string.login_failed_please_retry.stringRes(accountNo.toString()),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun CoroutineScope.updateLoginState(s: AccountState) {
        launch {
            _loginState.emit(s)
        }
    }
}

enum class HomeNavSelection(val id: Int) {
    MESSAGE(0),
    CONTACT(1),
    PROFILE(2)
}

val homeNaves = mapOf(
    HomeNavSelection.MESSAGE to HomeNav(
        selection = HomeNavSelection.MESSAGE,
        icon = Icons.Default.Message,
        label = R.string.home_nav_message,
        content = {
            val navController = LocalNavController.current
            HomeMessagePage(it) { contact, messageId ->
                navController.navigate("$NAV_CHAT/${contact.toNavArg(messageId)}")
            }
        }
    ),
    HomeNavSelection.CONTACT to HomeNav(
        selection = HomeNavSelection.CONTACT,
        icon = Icons.Default.Contacts,
        label = R.string.home_nav_contact,
        content = { HomeContactPage(it) }
    ),
    HomeNavSelection.PROFILE to HomeNav(
        selection = HomeNavSelection.PROFILE,
        icon = Icons.Default.AccountCircle,
        label = R.string.home_nav_profile,
        content = { HomeProfilePage(it) }
    )
)

data class HomeNav(
    val selection: HomeNavSelection,
    val icon: ImageVector,
    @StringRes val label: Int,
    val content: @Composable (PaddingValues) -> Unit,
)

sealed class AccountState(val bot: Long) {
    object Default : AccountState(-1)
    class Login(bot: Long, val state: LoginState) : AccountState(bot)
    class Online(bot: Long) : AccountState(bot)
    class Offline(bot: Long, val cause: String?) : AccountState(bot)
}

data class BasicAccountInfo(
    val id: Long,
    val nick: String,
    val avatarUrl: String?
)
