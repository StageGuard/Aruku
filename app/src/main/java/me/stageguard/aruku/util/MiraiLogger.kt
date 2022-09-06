package me.stageguard.aruku.util

import android.util.Log
import net.mamoe.mirai.utils.MiraiLogger

class ArukuMiraiLogger(
    override val identity: String?,
    override val isEnabled: Boolean
) : MiraiLogger {
    private val tag = toLogTag("Bot")
    override fun debug(message: String?) {
        Log.d(tag, message, null)
    }

    override fun debug(message: String?, e: Throwable?) {
        Log.d(tag, message, e)
    }

    override fun error(message: String?) {
        Log.e(tag, message, null)
    }

    override fun error(message: String?, e: Throwable?) {
        Log.e(tag, message, e)
    }

    override fun info(message: String?) {
        Log.i(tag, message, null)
    }

    override fun info(message: String?, e: Throwable?) {
        Log.i(tag, message, e)
    }

    override fun verbose(message: String?) {
        Log.v(tag, message, null)
    }

    override fun verbose(message: String?, e: Throwable?) {
        Log.v(tag, message, e)
    }

    override fun warning(message: String?) {
        Log.w(tag, message, null)
    }

    override fun warning(message: String?, e: Throwable?) {
        Log.w(tag, message, e)
    }
}