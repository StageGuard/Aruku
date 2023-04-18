package me.stageguard.aruku.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import me.stageguard.aruku.service.bridge.BotStateObserver
import me.stageguard.aruku.service.bridge.ServiceBridge
import me.stageguard.aruku.service.bridge.ServiceBridge_Proxy
import me.stageguard.aruku.service.parcel.AccountState
import me.stageguard.aruku.service.parcel.AccountState.OfflineCause
import me.stageguard.aruku.util.createAndroidLogger
import me.stageguard.aruku.util.tag
import kotlin.collections.set

class ServiceConnector(
    private val context: Context
) : ServiceConnection, LifecycleEventObserver {
    private val logger = createAndroidLogger("ServiceConnector")

    private var _delegate: ServiceBridge? = null
    val connected: MutableLiveData<Boolean> = MutableLiveData(false)

    val accountState: Flow<Map<Long, AccountState>>
        get() = channelFlow {
            @Suppress("LocalVariableName") val _delegate0 = _delegate ?: run {
                logger.w("Failed to build account state flow because service bridge is null.")
                return@channelFlow
            }
            val states: MutableMap<Long, AccountState> = mutableMapOf()

            // send initial state
            send(_delegate0.getLastBotState())
            // observe state changes
            _delegate0.attachBotStateObserver("", BotStateObserver { state ->
                if (state is AccountState.Offline && state.cause == OfflineCause.REMOVE_BOT) {
                    states.remove(state.account)
                    return@BotStateObserver
                }
                states[state.account] = state

                trySendBlocking(states).onFailure { logger.w("Failed to send account states.", it) }
            })

            awaitClose { _delegate0.detachBotStateObserver() }
        }
    val binder get() = _delegate

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        Log.d(tag(), "service is connected: $name")
        if (service != null) {
            _delegate = ServiceBridge_Proxy(service)
        } else {
            Log.w(tag(), "service binder is null while aruku service is connected.")
        }
        connected.value = true
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Log.d(tag(), "service is disconnected: $name")
        _delegate = null
        connected.value = false
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
                val bindResult = context.bindService(
                    Intent(context, ArukuMiraiService::class.java), this, Context.BIND_ABOVE_CLIENT
                )
                if (!bindResult) Log.e(tag(), "Cannot bind ArukuMiraiService.")
            }

            Lifecycle.Event.ON_DESTROY -> {
                try {
                    context.unbindService(this)
                } catch (ex: IllegalArgumentException) {
                    Log.w(tag(), "unable to unregister service.")
                }
            }

            else -> {}
        }
    }
}