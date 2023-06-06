package me.stageguard.aruku.mirai_core

import me.stageguard.aruku.common.createAndroidLogger
import net.mamoe.mirai.utils.MiraiLogger

class ArukuMiraiLogger(
    override val identity: String,
    override val isEnabled: Boolean
) : MiraiLogger {
    private val logger = createAndroidLogger(identity)
    override fun debug(message: String?) {
        logger.d(message.toString(), null)
    }

    override fun debug(message: String?, e: Throwable?) {
        logger.d(message.toString(), e)
    }

    override fun error(message: String?) {
        logger.e(message.toString(), null)
    }

    override fun error(message: String?, e: Throwable?) {
        logger.e(message.toString(), e)
    }

    override fun info(message: String?) {
        logger.i(message.toString(), null)
    }

    override fun info(message: String?, e: Throwable?) {
        logger.i(message.toString(), e)
    }

    override fun verbose(message: String?) {
        logger.d(message.toString(), null)
    }

    override fun verbose(message: String?, e: Throwable?) {
        logger.d(message.toString(), e)
    }

    override fun warning(message: String?) {
        logger.w(message.toString(), null)
    }

    override fun warning(message: String?, e: Throwable?) {
        logger.w(message.toString(), e)
    }
}