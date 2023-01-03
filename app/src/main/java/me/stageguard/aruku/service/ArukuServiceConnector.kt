package me.stageguard.aruku.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.*
import me.stageguard.aruku.util.tag
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class ArukuServiceConnector(
    private val context: Context
) : ServiceConnection, LifecycleEventObserver, ReadOnlyProperty<Any?, IArukuMiraiInterface> {
    private lateinit var _delegate: IArukuMiraiInterface
    val connected: MutableLiveData<Boolean> = MutableLiveData(false)

    private val _bots: MutableList<Long> = mutableListOf()
    private val _botsLiveData: MutableLiveData<List<Long>> = MutableLiveData()

    val bots: LiveData<List<Long>> = _botsLiveData

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        Log.d(tag(), "service is connected: $name")
        _delegate = IArukuMiraiInterface.Stub.asInterface(service)
        connected.value = true

        _delegate.addBotListObserver(toString(), object : IBotListObserver.Stub() {
            override fun onChange(newList: LongArray?) {
                val l = newList?.toList() ?: listOf()
                _bots.removeIf { it !in l }
                l.forEach { if (it !in _bots) _bots.add(it) }
                _botsLiveData.value = _bots
            }
        })
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Log.d(tag(), "service is disconnected: $name")
        connected.value = false
        _delegate.removeBotListObserver(toString())
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

    override fun getValue(thisRef: Any?, property: KProperty<*>): IArukuMiraiInterface {
        return _delegate
    }
}