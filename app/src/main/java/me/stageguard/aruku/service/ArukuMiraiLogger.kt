package me.stageguard.aruku.service

import android.util.Log
import net.mamoe.mirai.utils.MiraiLogger

class ArukuMiraiLogger(
    override val identity: String?,
    override val isEnabled: Boolean
) : MiraiLogger {
    override fun debug(message: String?) {
        Log.d(identity, message, null)
    }

    override fun debug(message: String?, e: Throwable?) {
        Log.d(identity, message, e)
    }

    override fun error(message: String?) {
        Log.e(identity, message, null)
    }

    override fun error(message: String?, e: Throwable?) {
        Log.e(identity, message, e)
    }

    override fun info(message: String?) {
        Log.i(identity, message, null)
    }

    override fun info(message: String?, e: Throwable?) {
        Log.i(identity, message, e)
    }

    override fun verbose(message: String?) {
        Log.v(identity, message, null)
    }

    override fun verbose(message: String?, e: Throwable?) {
        Log.v(identity, message, e)
    }

    override fun warning(message: String?) {
        Log.w(identity, message, null)
    }

    override fun warning(message: String?, e: Throwable?) {
        Log.w(identity, message, e)
    }
}