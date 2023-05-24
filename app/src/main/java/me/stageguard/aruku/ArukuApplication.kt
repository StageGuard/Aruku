package me.stageguard.aruku

import android.app.Application
import android.content.Intent
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import me.stageguard.aruku.service.ArukuMiraiService
import me.stageguard.aruku.util.FaceAPNGDecoder
import me.stageguard.aruku.util.createAndroidLogger
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import java.security.Security
import java.util.concurrent.atomic.AtomicBoolean

class ArukuApplication : Application(), ImageLoaderFactory {
    private val logger = createAndroidLogger()
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
            logger.w("Cannot replace original bouncy castle provider.", t)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this).components {
            add(FaceAPNGDecoder.Factory())
            add(if (Build.VERSION.SDK_INT >= 28) ImageDecoderDecoder.Factory() else GifDecoder.Factory())
        }.build()
    }
}