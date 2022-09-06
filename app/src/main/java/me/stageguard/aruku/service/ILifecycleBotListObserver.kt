package me.stageguard.aruku.service

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

abstract class ILifecycleBotListObserver(
    private val serviceInterface: IArukuMiraiInterface,
    private val block: (List<Long>) -> Unit
) : IBotListObserver.Stub(), LifecycleEventObserver {
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_CREATE -> serviceInterface.addBotListObserver(toString(), this)
            Lifecycle.Event.ON_DESTROY -> serviceInterface.removeBotListObserver(toString())
            else -> {}
        }
    }

    override fun onChange(newList: LongArray?) {
        block(newList?.toList() ?: listOf())
    }
}

fun IArukuMiraiInterface.observeBotList(owner: LifecycleOwner, block: (List<Long>) -> Unit) {
    val observer = object : ILifecycleBotListObserver(this, block) { }
    addBotListObserver(observer.toString(), observer)
    owner.lifecycle.addObserver(observer)
}