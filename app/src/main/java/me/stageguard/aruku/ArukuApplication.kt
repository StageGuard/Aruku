package me.stageguard.aruku

import android.app.Application
import android.content.Intent
import android.util.Log
import me.stageguard.aruku.preference.ArukuPreference
import me.stageguard.aruku.service.ArukuMiraiService
import me.stageguard.aruku.util.toLogTag
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
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

        startKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@ArukuApplication)
            modules(applicationModule)
        }

        registerComponentCallbacks(ArukuPreference)

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