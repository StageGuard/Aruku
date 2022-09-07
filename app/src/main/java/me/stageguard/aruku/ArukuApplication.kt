package me.stageguard.aruku

import android.app.Application
import android.content.Intent
import android.util.Log
import me.stageguard.aruku.service.ArukuMiraiService
import me.stageguard.aruku.util.toLogTag
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import java.util.concurrent.atomic.AtomicBoolean

class ArukuApplication : Application() {
    companion object {
        val initialized = AtomicBoolean(false)
        lateinit var INSTANCE: ArukuApplication
    }

    override fun onCreate() {
        initialized.compareAndSet(false, true)
        INSTANCE = this
        startService(Intent(this, ArukuMiraiService::class.java))
        super.onCreate()
    }

    init {
        try {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.addProvider(BouncyCastleProvider())
        } catch (t: Throwable) {
            Log.w(toLogTag(), "Cannot replace original bouncy castle provider.", t)
        }
    }
}