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
import me.stageguard.aruku.util.tag
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class ArukuServiceConnector(
    private val context: Context
) : ServiceConnection, LifecycleEventObserver, ReadOnlyProperty<Nothing?, IArukuMiraiInterface?> {
    private var _delegate: IArukuMiraiInterface? = null
    val connected: MutableLiveData<Boolean> = MutableLiveData(false)

    private val _botsLiveData: MutableLiveData<List<Long>> = MutableLiveData()

    val bots: LiveData<List<Long>> = _botsLiveData

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        Log.d(tag(), "service is connected: $name")
        _delegate = IArukuMiraiInterface.Stub.asInterface(service).apply {
            addBotListObserver(
                this@ArukuServiceConnector.toString(),
                object : IBotListObserver.Stub() {
                    override fun onChange(newList: LongArray?) {
                        _botsLiveData.value = newList?.toList() ?: listOf()
                    }
                }
            )
        }
        connected.value = true
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Log.d(tag(), "service is disconnected: $name")
        _delegate?.removeBotListObserver(this@ArukuServiceConnector.toString())
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

    override fun getValue(thisRef: Nothing?, property: KProperty<*>): IArukuMiraiInterface? {
        if (_delegate == null) {
            Log.w(tag(), "binder IArukuMiraiInterface hasn't yet initialized.")
        }
        return _delegate
    }
}