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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import me.stageguard.aruku.service.bridge.BotObserverBridge
import me.stageguard.aruku.service.bridge.ServiceBridge
import me.stageguard.aruku.service.bridge.ServiceBridge_Proxy
import me.stageguard.aruku.util.tag
import remoter.annotations.ParamOut
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class ServiceConnector(
    private val context: Context
) : ServiceConnection, LifecycleEventObserver, ReadOnlyProperty<Nothing?, ServiceBridge?> {
    private var _delegate: ServiceBridge? = null
    val connected: MutableLiveData<Boolean> = MutableLiveData(false)

    private val _botsLiveData: MutableLiveData<List<Long>> = MutableLiveData()

    val bots: LiveData<List<Long>> = _botsLiveData

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        Log.d(tag(), "service is connected: $name")
        if (service != null) {
            val proxy = ServiceBridge_Proxy(service)
            proxy.addBotListObserver(
                this@ServiceConnector.toString(),
                object : BotObserverBridge {
                    override fun onChange(@ParamOut bots: List<Long>) {
                        _botsLiveData.postValue(bots)
                    }
                }
            )
            _botsLiveData.value = proxy.getBots()
            _delegate = proxy
        } else {
            Log.w(tag(), "service binder is null while aruku service is connected.")
        }
        connected.value = true
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Log.d(tag(), "service is disconnected: $name")
        _delegate?.removeBotListObserver(this@ServiceConnector.toString())
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

    override fun getValue(thisRef: Nothing?, property: KProperty<*>): ServiceBridge? {
        if (_delegate == null) {
            Log.w(tag(), "binder IArukuMiraiInterface hasn't yet initialized.")
        }
        return _delegate
    }
}