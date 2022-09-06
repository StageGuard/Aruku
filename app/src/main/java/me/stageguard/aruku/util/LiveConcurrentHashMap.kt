package me.stageguard.aruku.util

import androidx.lifecycle.Observer
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction
import java.util.function.Function

class LiveConcurrentHashMap<K : Any, V : Any>(
    private val observer: Observer<Map<K, V>> = Observer {  }
) : ConcurrentHashMap<K, V>() {
    private fun <T : Any?> T.observe(condition: T.() -> Boolean = { true }): T {
        if (condition()) observer.onChanged(this@LiveConcurrentHashMap.toMap())
        return this
    }

    private fun <T : Any?> T.notNullObserve(): T  = observe { this != null }

    override fun putIfAbsent(key: K, value: V): V? {
        return super.putIfAbsent(key, value).also {
            if (it != value) observer.onChanged(toMap())
        }
    }

    override fun remove(key: K): V? {
        return super.remove(key).notNullObserve()
    }


    override fun remove(key: K, value: V): Boolean {
        return super.remove(key, value).observe { this }
    }

    override fun clear() {
        super.clear()
        observer.onChanged(toMap())
    }

    override fun put(key: K, value: V): V? {
        return super.put(key, value).observe()
    }

    override fun replace(key: K, value: V): V? {
        return super.replace(key, value).notNullObserve()
    }

    override fun replace(key: K, oldValue: V, newValue: V): Boolean {
        return super.replace(key, oldValue, newValue).observe { this }
    }

    override fun replaceAll(function: BiFunction<in K, in V, out V>) {
        super.replaceAll(function).observe()
    }

    override fun compute(key: K, remappingFunction: BiFunction<in K, in V?, out V?>): V? {
        return super.compute(key, remappingFunction).notNullObserve()
    }

    override fun computeIfAbsent(key: K, mappingFunction: Function<in K, out V>): V {
        return super.computeIfAbsent(key, mappingFunction).observe()
    }

    override fun computeIfPresent(key: K, remappingFunction: BiFunction<in K, in V, out V?>): V? {
        return super.computeIfPresent(key, remappingFunction).notNullObserve()
    }

    override fun putAll(from: Map<out K, V>) {
        super.putAll(from).observe()
    }
}