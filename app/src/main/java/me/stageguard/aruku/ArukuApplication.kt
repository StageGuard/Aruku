package me.stageguard.aruku

import android.app.Application
import android.content.Intent
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import me.stageguard.aruku.service.ArukuMiraiService
import me.stageguard.aruku.util.AnimatedPngDecoder
import me.stageguard.aruku.util.tag
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import java.security.Security
import java.util.concurrent.atomic.AtomicBoolean

class ArukuApplication : Application(), ImageLoaderFactory {
    companion object {
        val initialized = AtomicBoolean(false)
        lateinit var INSTANCE: ArukuApplication
    }

    override fun onCreate() {
        startKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@ArukuApplication)
            modules(applicationModule)
        }

        super.onCreate()

        initialized.compareAndSet(false, true)
        INSTANCE = this

        startService(Intent(this, ArukuMiraiService::class.java))
    }

    init {
        try {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.addProvider(BouncyCastleProvider())
        } catch (t: Throwable) {
            Log.w(tag(), "Cannot replace original bouncy castle provider.", t)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this).components {
            add(AnimatedPngDecoder.Factory())
        }.build()
    }
}