package me.stageguard.aruku.ui.page.home

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import me.stageguard.aruku.ArukuApplication
import me.stageguard.aruku.R
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.service.IArukuMiraiInterface
import me.stageguard.aruku.service.ILoginSolver
import me.stageguard.aruku.ui.page.login.CaptchaType
import me.stageguard.aruku.ui.page.login.LoginState
import me.stageguard.aruku.util.stringRes
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.AvatarSpec
import net.mamoe.mirai.event.ListeningStatus
import net.mamoe.mirai.event.events.BotOfflineEvent
import java.time.LocalDateTime

class HomeViewModel(
    private val arukuServiceInterface: IArukuMiraiInterface,
    private val _accountList: SnapshotStateList<Long>,
    private val database: ArukuDatabase,
) : ViewModel() {

    private val captchaChannel = Channel<String?>()
    private val loginSolver = object : ILoginSolver.Stub() {
        override fun onSolvePicCaptcha(bot: Long, data: ByteArray?): String? {
            accountState.value = if (data == null) {
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
            accountState.value = AccountState.Login(bot, LoginState.Logging)
            return captchaResult
        }

        override fun onSolveSliderCaptcha(bot: Long, url: String?): String? {
            accountState.value = if (url == null) {
                AccountState.Offline(bot, "Slider captcha url is null.")
            } else {
                AccountState.Login(bot, LoginState.CaptchaRequired(bot, CaptchaType.Slider(bot, url)))
            }

            val captchaResult = runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
            accountState.value = AccountState.Login(bot, LoginState.Logging)
            return captchaResult
        }

        override fun onSolveUnsafeDeviceLoginVerify(bot: Long, url: String?): String? {
            accountState.value = if (url == null) {
                AccountState.Offline(bot, "UnsafeDeviceLogin captcha url is null.")
            } else {
                AccountState.Login(bot, LoginState.CaptchaRequired(bot, CaptchaType.UnsafeDevice(bot, url)))
            }

            val captchaResult = runBlocking(viewModelScope.coroutineContext) { captchaChannel.receive() }
            accountState.value = AccountState.Login(bot, LoginState.Logging)
            return captchaResult
        }

        override fun onLoginSuccess(bot: Long) {
            accountState.value = AccountState.Online(bot)
        }

        override fun onLoginFailed(bot: Long, botKilled: Boolean, cause: String?) {
            accountState.value = AccountState.Login(bot, LoginState.Failed(bot, cause.toString()))
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
    val accountState = mutableStateOf<AccountState>(AccountState.Default)
    val messages get() = _messageSequences


    fun observeAccountState(account: Bot) {
        activeAccountLoginSolver?.let { arukuServiceInterface.removeLoginSolver(it.first) }

        if (!account.isOnline) {
            accountState.value = AccountState.Login(account.id, LoginState.Logging)

            val currentLoginSolver = account.id to loginSolver
            arukuServiceInterface.addLoginSolver(currentLoginSolver.first, currentLoginSolver.second)
            activeAccountLoginSolver = currentLoginSolver
        } else {
            accountState.value = AccountState.Online(account.id)
            account.eventChannel.parentScope(viewModelScope).subscribe<BotOfflineEvent> {
                if (!viewModelScope.isActive) return@subscribe ListeningStatus.STOPPED
                accountState.value = AccountState.Offline(it.bot.id, it.toString())
                return@subscribe ListeningStatus.LISTENING
            }
        }
    }

    fun submitCaptcha(accountNo: Long, result: String? = null) {
        accountState.value = AccountState.Login(accountNo, LoginState.Logging)
        captchaChannel.trySend(result)
    }

    fun loginFailed(accountNo: Long) {
        accountState.value = AccountState.Offline(accountNo, null)
        Toast.makeText(
            ArukuApplication.INSTANCE.applicationContext,
            R.string.login_failed_please_retry.stringRes(accountNo.toString()),
            Toast.LENGTH_SHORT
        ).show()
    }

    fun getAccountBasicInfo(): List<BasicAccountInfo> {
        return _accountList.map {
            val b = Bot.getInstance(it)
            return@map BasicAccountInfo(b.id, b.nick, b.avatarUrl(AvatarSpec.ORIGINAL))
        }
    }

    fun observeMessagePreview(account: Bot) {

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
    val avatar: ImageBitmap,
    val name: String,
    val preview: String,
    val time: LocalDateTime,
    val unreadCount: Int
)