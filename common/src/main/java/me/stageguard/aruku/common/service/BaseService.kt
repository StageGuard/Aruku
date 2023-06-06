package me.stageguard.aruku.common.service

import kotlinx.coroutines.CoroutineScope
import me.stageguard.aruku.common.service.bridge.ServiceBridge
import java.io.File
import kotlin.coroutines.CoroutineContext

abstract class BaseService(
    override val coroutineContext: CoroutineContext,
    open val workingDir: File,
) : ServiceBridge, CoroutineScope by CoroutineScope(coroutineContext) {
    abstract fun onCreate()
    abstract fun onDestroy()
}