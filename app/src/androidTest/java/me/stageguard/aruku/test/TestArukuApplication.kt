package me.stageguard.aruku.test

import android.app.Application
import android.content.Intent
import me.stageguard.aruku.service.ArukuService
import me.stageguard.aruku.util.createAndroidLogger
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import java.security.Security
import java.util.concurrent.atomic.AtomicBoolean

class TestArukuApplication : Application() {
    private val logger = createAndroidLogger()
    companion object {
        val initialized = AtomicBoolean(false)
        lateinit var INSTANCE: Application
    }

    override fun onCreate() {
        initialized.compareAndSet(false, true)
        INSTANCE = this

        startKoin {
            androidLogger()
            androidContext(this@TestArukuApplication)
//            modules(testBotFactoryModule)
        }

        startService(Intent(this, ArukuService::class.java))
        super.onCreate()
    }

    init {
        try {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.addProvider(BouncyCastleProvider())
        } catch (t: Throwable) {
            logger.w("Cannot replace original bouncy castle provider.", t)
        }
    }
}