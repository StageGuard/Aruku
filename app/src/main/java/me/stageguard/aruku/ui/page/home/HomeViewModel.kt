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
import androidx.compose.runtime.State
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.stageguard.aruku.ArukuApplication
import me.stageguard.aruku.R
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.preference.ArukuPreference
import me.stageguard.aruku.service.IArukuMiraiInterface
import me.stageguard.aruku.service.ILoginSolver
import me.stageguard.aruku.ui.page.home.contact.HomeContactPage
import me.stageguard.aruku.ui.page.home.message.HomeMessagePage
import me.stageguard.aruku.ui.page.home.profile.HomeProfilePage
import me.stageguard.aruku.ui.page.login.CaptchaType
import me.stageguard.aruku.ui.page.login.LoginState
import me.stageguard.aruku.util.stringRes
import me.stageguard.aruku.util.tag
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.AvatarSpec
import net.mamoe.mirai.event.ListeningStatus
import net.mamoe.mirai.event.events.BotOfflineEvent

class HomeViewModel(
    private val arukuServiceInterface: IArukuMiraiInterface,
    private val _accountList: LiveData<List<Long>>,
    private val database: ArukuDatabase,
) : ViewModel() {
    // represents state of current account
    // it is only changed at [observeAccountState] and [loginSolver]
    private val _loginState = mutableStateOf<AccountState>(AccountState.Default)
    val loginState: State<AccountState> get() = _loginState

    private val captchaChannel = Channel<String?>()
    private val loginSolver = object : ILoginSolver.Stub() {
        override fun onSolvePicCaptcha(bot: Long, data: ByteArray?): String? {
            _loginState.value = if (data == null) {
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
            val captchaResult =
                runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
            _loginState.value = AccountState.Login(bot, LoginState.Logging)
            return captchaResult
        }

        override fun onSolveSliderCaptcha(bot: Long, url: String?): String? {
            _loginState.value = if (url == null) {
                AccountState.Offline(bot, "Slider captcha url is null.")
            } else {
                AccountState.Login(
                    bot,
                    LoginState.CaptchaRequired(bot, CaptchaType.Slider(bot, url))
                )
            }

            val captchaResult =
                runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
            _loginState.value = AccountState.Login(bot, LoginState.Logging)
            return captchaResult
        }

        override fun onSolveUnsafeDeviceLoginVerify(bot: Long, url: String?): String? {
            _loginState.value = if (url == null) {
                AccountState.Offline(bot, "UnsafeDeviceLogin captcha url is null.")
            } else {
                AccountState.Login(
                    bot,
                    LoginState.CaptchaRequired(bot, CaptchaType.UnsafeDevice(bot, url))
                )
            }

            val captchaResult =
                runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
            _loginState.value = AccountState.Login(bot, LoginState.Logging)
            return captchaResult
        }

        override fun onSolveSMSRequest(bot: Long, phone: String?): String? {
            _loginState.value =
                AccountState.Login(
                    bot,
                    LoginState.CaptchaRequired(bot, CaptchaType.SMSRequest(bot, phone ?: "-"))
                )

            val captchaResult =
                runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
            _loginState.value = AccountState.Login(bot, LoginState.Logging)
            return captchaResult
        }

        override fun onLoginSuccess(bot: Long) {
            _loginState.value = AccountState.Online(bot)
        }

        override fun onLoginFailed(bot: Long, botKilled: Boolean, cause: String?) {
            _loginState.value = AccountState.Login(bot, LoginState.Failed(bot, cause.toString()))
            Toast.makeText(
                ArukuApplication.INSTANCE.applicationContext,
                R.string.login_failed_please_retry.stringRes(bot.toString()),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    private var activeAccountLoginSolver: Pair<Long, ILoginSolver>? = null

    val currentNavSelection = mutableStateOf(homeNaves[HomeNavSelection.MESSAGE]!!)

    // observe account state at home page
    fun observeAccountState(account: Long?) {
        val bot = if (account != null) Bot.getInstanceOrNull(account) else null
        Log.i(tag(), "observeAccountState $bot")
        // no bot provided from LocalBot, maybe first launch login or no account.
        if (bot == null) {
            val activeBot = ArukuPreference.activeBot
            // first launch login
            if (activeBot != null) { // state: Logging
                Log.i(tag(), "First launch and observing background account login state.")
                _loginState.value = AccountState.Login(activeBot, LoginState.Logging)

                activeAccountLoginSolver?.let { arukuServiceInterface.removeLoginSolver(it.first) }
                val currentLoginSolver = activeBot to loginSolver
                arukuServiceInterface.addLoginSolver(activeBot, currentLoginSolver.second)
                activeAccountLoginSolver = currentLoginSolver
            }
        } else {
            // bot is provided.
            // if offline, it is either logout manually or appears offline.
            if (!bot.isOnline) {
                viewModelScope.launch(Dispatchers.IO) {
                    val dbAccount = database { accounts()[bot.id].singleOrNull() }
                    if (dbAccount == null) {
                        Log.w(tag(), "LocalBot is provided but the account is not found in database.")
                        return@launch
                    }

                    if (!dbAccount.isOfflineManually) { // state: Logging
                        Log.i(tag(), "Bot is now logging.")
                        _loginState.value = AccountState.Login(bot.id, LoginState.Logging)

                        activeAccountLoginSolver?.let { arukuServiceInterface.removeLoginSolver(it.first) }
                        val currentLoginSolver = bot.id to loginSolver
                        arukuServiceInterface.addLoginSolver(
                            currentLoginSolver.first,
                            currentLoginSolver.second
                        )
                        activeAccountLoginSolver = currentLoginSolver
                    } else { // state: Offline
                        _loginState.value = AccountState.Offline(bot.id, null)
                    }
                }
            } else { // state: Online
                Log.i(tag(), "Bot is online.")
                _loginState.value = AccountState.Online(bot.id)
                bot.eventChannel.parentScope(viewModelScope).subscribe<BotOfflineEvent> {
                    if (!viewModelScope.isActive) return@subscribe ListeningStatus.STOPPED
                    _loginState.value = AccountState.Offline(it.bot.id, it.toString())
                    return@subscribe ListeningStatus.LISTENING
                }
            }
        }
    }

    fun submitCaptcha(accountNo: Long, result: String? = null) {
        _loginState.value = AccountState.Login(accountNo, LoginState.Logging)
        captchaChannel.trySend(result)
    }

    fun cancelLogin(accountNo: Long) {
        _loginState.value = AccountState.Offline(accountNo, null)
        viewModelScope.launch {
            val accountDao = database { accounts() }
            val dbAccount = accountDao[accountNo].singleOrNull()
            if (dbAccount != null) {
                accountDao.update(dbAccount.apply { isOfflineManually = true })
            }
        }
        Toast.makeText(
            ArukuApplication.INSTANCE.applicationContext,
            R.string.login_failed_please_retry.stringRes(accountNo.toString()),
            Toast.LENGTH_SHORT
        ).show()
    }

    @Composable
    fun getAccountBasicInfo(): List<BasicAccountInfo> {
        val state = _accountList.observeAsState(listOf())

        return state.value.filter { Bot.getInstanceOrNull(it) != null }.map {
            val b = Bot.getInstance(it)
            BasicAccountInfo(b.id, b.nick, b.avatarUrl(AvatarSpec.ORIGINAL))
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
        content = { HomeMessagePage(it) }
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
    val topBar: (@Composable (PaddingValues) -> Unit)? = null,
    val overly: (@Composable () -> Unit)? = null,
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
