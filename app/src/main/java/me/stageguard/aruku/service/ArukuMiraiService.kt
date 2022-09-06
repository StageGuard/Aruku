package me.stageguard.aruku.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.single
import me.stageguard.aruku.ArukuApplication
import me.stageguard.aruku.preference.accountStore
import me.stageguard.aruku.preference.proto.AccountsOuterClass.Accounts
import me.stageguard.aruku.preference.proto.AccountsOuterClass.Accounts.AccountInfo as AccountInfoProto
import me.stageguard.aruku.service.parcel.AccountInfo as AccountInfoParcel
import me.stageguard.aruku.util.ArukuMiraiLogger
import me.stageguard.aruku.util.LiveConcurrentHashMap
import me.stageguard.aruku.util.toLogTag
import net.mamoe.mirai.Bot
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.network.CustomLoginFailedException
import net.mamoe.mirai.network.LoginFailedException
import net.mamoe.mirai.utils.BotConfiguration.MiraiProtocol
import net.mamoe.mirai.utils.BotConfiguration.HeartbeatStrategy
import net.mamoe.mirai.utils.LoginSolver
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class ArukuMiraiService : LifecycleService() {
    private val bots: LiveConcurrentHashMap<Long, Bot> = LiveConcurrentHashMap { newList ->
        botListObservers.forEach { (_, observer) -> observer.onChange(newList.keys.toLongArray()) }
    }
    private val botJobs: ConcurrentHashMap<Long, Job> = ConcurrentHashMap()

    private val botListObservers: MutableMap<String, IBotListObserver> = mutableMapOf()
    private val loginSolvers: MutableMap<Long, ILoginSolver> = mutableMapOf()

    private val binderInterface = object : IArukuMiraiInterface.Stub() {
        override fun addBot(info: AccountInfoParcel?, alsoLogin: Boolean): Boolean {
            Log.i(toLogTag(), "addBot(info=$info)")
            if (info == null) return false
            addBot(info)
            if (alsoLogin) login(info.accountNo)
            return true
        }

        override fun removeBot(accountNo: Long): Boolean {
            return this@ArukuMiraiService.removeBot(accountNo)
        }

        override fun getBots(): LongArray {
            return this@ArukuMiraiService.bots.map { it.key }.toLongArray()
        }

        override fun loginAll() {
            this@ArukuMiraiService.bots.forEach { (no, _) -> login(no) }
        }

        override fun login(accountNo: Long): Boolean {
            return this@ArukuMiraiService.login(accountNo)
        }

        override fun addBotListObserver(identity: String?, observer: IBotListObserver?) {
            if (identity != null && observer != null) {
                botListObservers[identity] = observer
            } else {
                Log.w(this@ArukuMiraiService.toLogTag(), "Empty identity or observer for botListObservers.")
            }
        }

        override fun removeBotListObserver(identity: String?) {
            botListObservers.remove(identity)
        }

        override fun addLoginSolver(bot: Long, solver: ILoginSolver?) {
            if (solver != null) {
                loginSolvers[bot] = solver
            } else {
                Log.w(this@ArukuMiraiService.toLogTag(), "Empty login solver for loginSolvers.")
            }
        }

        override fun removeLoginSolver(bot: Long) {
            loginSolvers.remove(bot)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (ArukuApplication.initialized.get()) {
            val context = ArukuApplication.INSTANCE.applicationContext
            lifecycleScope.launch {
                val accounts = context.accountStore.single().accountMap
                accounts.forEach { (id, info) ->
                    bots[id] = createBot(info)
                }
            }
            Log.i(toLogTag(), "ArukuMiraiService is created.")
        } else {
            Log.e(toLogTag(), "ArukuApplication is not initialized yet.")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binderInterface
    }

    override fun onDestroy() {
        bots.forEach { (_, bot) -> removeBot(bot.id) }
        super.onDestroy()
    }

    private fun notification() {

    }

    private fun addBot(accountInfo: AccountInfoParcel): Boolean {
        if (this@ArukuMiraiService.bots[accountInfo.accountNo] != null) {
            this@ArukuMiraiService.removeBot(accountInfo.accountNo)
        }
        this@ArukuMiraiService.bots[accountInfo.accountNo] = createBot(accountInfo.into())
        return true
    }

    private fun login(accountNo: Long): Boolean {
        val targetBot = this@ArukuMiraiService.bots[accountNo]
        return if (targetBot != null) {
            if (!targetBot.isOnline) {
                val job = lifecycleScope.launch(context = CoroutineExceptionHandler { _, throwable ->
                    if (throwable is LoginFailedException) {
                        Log.e(toLogTag(), "login $accountNo failed.", throwable)
                        loginSolvers[accountNo]?.onLoginFailed(accountNo, throwable.killBot, throwable.message)
                    }
                }) {
                    targetBot.login()
                    loginSolvers[accountNo]?.onLoginSuccess(accountNo)
                    targetBot.join()
                }
                val existingBotJob = botJobs[accountNo]
                if (existingBotJob != null) {
                    existingBotJob.cancel()
                    botJobs.remove(accountNo, existingBotJob)
                }
                botJobs[accountNo] = job
            }
            true
        } else false
    }

    private fun removeBot(accountNo: Long) : Boolean {
        val bot = bots[accountNo]
        val botJob = botJobs[accountNo]
        return bot ?.run {
            lifecycleScope.launch(Dispatchers.Main) {
                bot.closeAndJoin()
                botJob?.cancelAndJoin()
            }
            bots.remove(accountNo)
            botJobs.remove(accountNo)
            true
        } ?: false
    }

    private fun createBot(accountInfo: AccountInfoProto): Bot {
        return accountInfo.run {
            BotFactory.newBot(accountNo, passwordMd5) {
                parentCoroutineContext = lifecycleScope.coroutineContext
                protocol = when(getProtocol()) {
                    Accounts.login_protocol.ANDROID_PHONE -> MiraiProtocol.ANDROID_PHONE
                    Accounts.login_protocol.ANDROID_PAD -> MiraiProtocol.ANDROID_PAD
                    Accounts.login_protocol.ANDROID_WATCH -> MiraiProtocol.ANDROID_WATCH
                    Accounts.login_protocol.MACOS -> MiraiProtocol.MACOS
                    Accounts.login_protocol.IPAD -> MiraiProtocol.IPAD

                    Accounts.login_protocol.UNRECOGNIZED, null -> MiraiProtocol.ANDROID_PHONE
                }
                autoReconnectOnForceOffline = autoReconnect
                heartbeatStrategy = when(getHeartbeatStrategy()) {
                    Accounts.heartbeat_strategy.STAT_HB -> HeartbeatStrategy.STAT_HB
                    Accounts.heartbeat_strategy.REGISTER -> HeartbeatStrategy.REGISTER
                    Accounts.heartbeat_strategy.NONE -> HeartbeatStrategy.NONE

                    Accounts.heartbeat_strategy.UNRECOGNIZED, null -> HeartbeatStrategy.STAT_HB
                }
                heartbeatPeriodMillis = getHeartbeatPeriodMillis()
                heartbeatTimeoutMillis = getHeartbeatTimeoutMillis()
                reconnectionRetryTimes = getReconnectionRetryTimes()
                statHeartbeatPeriodMillis = getStatHeartbeatPeriodMillis()

                botLoggerSupplier = { ArukuMiraiLogger("Bot.${it.id}", true) }
                networkLoggerSupplier = { ArukuMiraiLogger("Net.${it.id}", true) }

                loginSolver = object : LoginSolver() {
                    override suspend fun onSolvePicCaptcha(bot: Bot, data: ByteArray): String {
                        return loginSolvers[bot.id]?.onSolvePicCaptcha(bot.id, data) ?:
                        throw object : CustomLoginFailedException(false, "no login solver for bot ${bot.id}") {}
                    }

                    override suspend fun onSolveSliderCaptcha(bot: Bot, url: String): String {
                        return loginSolvers[bot.id]?.onSolveSliderCaptcha(bot.id, url) ?:
                        throw object : CustomLoginFailedException(false, "no login solver for bot ${bot.id}") {}
                    }

                    override suspend fun onSolveUnsafeDeviceLoginVerify(bot: Bot, url: String): String {
                        return loginSolvers[bot.id]?.onSolveUnsafeDeviceLoginVerify(bot.id, url) ?:
                        throw object : CustomLoginFailedException(false, "no login solver for bot ${bot.id}") {}
                    }
                }
            }
        }
    }

    class Connector(
        private val context: Context
    ) : ServiceConnection, LifecycleEventObserver, ReadOnlyProperty<Any?, IArukuMiraiInterface> {
        private lateinit var _delegate: IArukuMiraiInterface
        val connected: MutableLiveData<Boolean> = MutableLiveData(false)

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(toLogTag(), "service is connected: $name")
            _delegate = IArukuMiraiInterface.Stub.asInterface(service)
            connected.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(toLogTag(), "service is disconnected: $name")
            connected.value = false
        }

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    val bindResult = context.bindService(
                        Intent(context, ArukuMiraiService::class.java), this, Context.BIND_ABOVE_CLIENT
                    )
                    if (!bindResult) Log.e(toLogTag(), "Cannot bind ArukuMiraiService.")
                }
                Lifecycle.Event.ON_DESTROY -> {
                    context.unbindService(this)
                }

                else -> {}
            }
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): IArukuMiraiInterface {
            return _delegate
        }
    }
}