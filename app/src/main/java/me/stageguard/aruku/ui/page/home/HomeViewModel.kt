package me.stageguard.aruku.ui.page.home

import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import me.stageguard.aruku.ArukuApplication
import me.stageguard.aruku.R
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.preference.ArukuPreference
import me.stageguard.aruku.service.IArukuMiraiInterface
import me.stageguard.aruku.service.ILoginSolver
import me.stageguard.aruku.service.parcel.ArukuMessageType
import me.stageguard.aruku.ui.page.login.CaptchaType
import me.stageguard.aruku.ui.page.login.LoginState
import me.stageguard.aruku.util.stringRes
import me.stageguard.aruku.util.toLogTag
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.AvatarSpec
import net.mamoe.mirai.event.ListeningStatus
import net.mamoe.mirai.event.events.BotOfflineEvent
import java.time.LocalDateTime
import java.time.ZoneOffset

class HomeViewModel(
    private val arukuServiceInterface: IArukuMiraiInterface,
    private val _accountList: LiveData<List<Long>>,
    private val database: ArukuDatabase,
) : ViewModel() {

    private val captchaChannel = Channel<String?>()
    private val loginSolver = object : ILoginSolver.Stub() {
        override fun onSolvePicCaptcha(bot: Long, data: ByteArray?): String? {
            loginState.value = if (data == null) {
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
            val captchaResult = runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
            loginState.value = AccountState.Login(bot, LoginState.Logging)
            return captchaResult
        }

        override fun onSolveSliderCaptcha(bot: Long, url: String?): String? {
            loginState.value = if (url == null) {
                AccountState.Offline(bot, "Slider captcha url is null.")
            } else {
                AccountState.Login(bot, LoginState.CaptchaRequired(bot, CaptchaType.Slider(bot, url)))
            }

            val captchaResult = runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
            loginState.value = AccountState.Login(bot, LoginState.Logging)
            return captchaResult
        }

        override fun onSolveUnsafeDeviceLoginVerify(bot: Long, url: String?): String? {
            loginState.value = if (url == null) {
                AccountState.Offline(bot, "UnsafeDeviceLogin captcha url is null.")
            } else {
                AccountState.Login(bot, LoginState.CaptchaRequired(bot, CaptchaType.UnsafeDevice(bot, url)))
            }

            val captchaResult = runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
            loginState.value = AccountState.Login(bot, LoginState.Logging)
            return captchaResult
        }

        override fun onSolveSMSRequest(bot: Long, phone: String?): String? {
            loginState.value =
                AccountState.Login(bot, LoginState.CaptchaRequired(bot, CaptchaType.SMSRequest(bot, phone ?: "-")))

            val captchaResult = runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
            loginState.value = AccountState.Login(bot, LoginState.Logging)
            return captchaResult
        }

        override fun onLoginSuccess(bot: Long) {
            loginState.value = AccountState.Online(bot)
        }

        override fun onLoginFailed(bot: Long, botKilled: Boolean, cause: String?) {
            loginState.value = AccountState.Login(bot, LoginState.Failed(bot, cause.toString()))
            Toast.makeText(
                ArukuApplication.INSTANCE.applicationContext,
                R.string.login_failed_please_retry.stringRes(bot.toString()),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    private var activeAccountLoginSolver: Pair<Long, ILoginSolver>? = null
    private val _messageSequences = mutableStateListOf<SimpleMessagePreview>()

    val currentNavSelection = mutableStateOf(homeNavs[HomeNavSelection.MESSAGE]!!)

    // represents state of current account
    // it is only changed at [observeAccountState] and [loginSolver]
    val loginState = mutableStateOf<AccountState>(AccountState.Default)
    val messages get() = _messageSequences

    // observe account state at home page
    fun observeAccountState(account: Long?) {
        val bot = if (account != null) Bot.getInstanceOrNull(account) else null
        // no bot provided from LocalBot, maybe first launch login or no account.
        if (bot == null) {
            val activeBot = ArukuPreference.activeBot
            // first launch login
            if (activeBot != null) { // state: Logging
                Log.i(toLogTag(), "First launch and observing background account login state.")
                loginState.value = AccountState.Login(activeBot, LoginState.Logging)

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
                        Log.w(toLogTag(), "LocalBot is provided but the bot is not stored in database.")
                        return@launch
                    }

                    if (!dbAccount.isOfflineManually) { // state: Logging
                        loginState.value = AccountState.Login(bot.id, LoginState.Logging)

                        activeAccountLoginSolver?.let { arukuServiceInterface.removeLoginSolver(it.first) }
                        val currentLoginSolver = bot.id to loginSolver
                        arukuServiceInterface.addLoginSolver(currentLoginSolver.first, currentLoginSolver.second)
                        activeAccountLoginSolver = currentLoginSolver
                    } else { // state: Offline
                        loginState.value = AccountState.Offline(bot.id, null)
                    }
                }
            } else { // state: Online
                loginState.value = AccountState.Online(bot.id)
                bot.eventChannel.parentScope(viewModelScope).subscribe<BotOfflineEvent> {
                    if (!viewModelScope.isActive) return@subscribe ListeningStatus.STOPPED
                    loginState.value = AccountState.Offline(it.bot.id, it.toString())
                    return@subscribe ListeningStatus.LISTENING
                }
            }
        }
    }

    fun submitCaptcha(accountNo: Long, result: String? = null) {
        loginState.value = AccountState.Login(accountNo, LoginState.Logging)
        captchaChannel.trySend(result)
    }

    fun cancelLogin(accountNo: Long) {
        loginState.value = AccountState.Offline(accountNo, null)
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

    // observe message preview changes in message page
    context(CoroutineScope) fun observeMessagePreview(account: Long) {
        val messageFlow = database { messagePreview().getMessages(account) }
        launch {
            messageFlow.collect { msg ->
                val subjects = msg.map { it.type to it.subject }
                _messageSequences.removeIf { it.type to it.subject in subjects }
                _messageSequences.addAll(msg.map {
                    SimpleMessagePreview(
                        type = it.type,
                        subject = it.subject,
                        avatarData = arukuServiceInterface.getAvatar(account, it.type.ordinal, it.subject),
                        name = arukuServiceInterface.getNickname(account, it.type.ordinal, it.subject)
                            ?: it.subject.toString(),
                        preview = it.previewContent,
                        time = LocalDateTime.ofEpochSecond(it.time, 0, ZoneOffset.UTC),
                        unreadCount = 1
                    )
                })
                _messageSequences.sortByDescending { it.time }
            }
        }
    }
}

enum class HomeNavSelection(val id: Int) {
    MESSAGE(0),
    CONTACT(1),
    PROFILE(2)
}

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

data class SimpleMessagePreview(
    val type: ArukuMessageType,
    val subject: Long,
    val avatarData: Any?,
    val name: String,
    val preview: String,
    val time: LocalDateTime,
    val unreadCount: Int
)