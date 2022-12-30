package me.stageguard.aruku.util

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.stageguard.aruku.ArukuApplication
import me.stageguard.aruku.ui.LocalStringLocale

/**
 * Created by LoliBall on 2022/9/6 17:36.
 * https://github.com/WhichWho
 */
class StringLocale(private val applicationContext: Context? = null) {
    @Composable
    infix fun id(@StringRes id: Int): String {
        return id(id, *arrayOf())
    }

    @Composable
    fun id(@StringRes id: Int, vararg args: Any): String {
        return applicationContext?.getString(id, *args) ?: stringResource(id, *args)
    }
}

val @receiver:StringRes Int.stringResC: String
    @Composable get() = LocalStringLocale.current id this

@Composable
fun @receiver:StringRes Int.stringResC(vararg args: Any): String {
    return LocalStringLocale.current.id(this, *args)
}

val @receiver:StringRes Int.stringRes: String
    get() = ArukuApplication.INSTANCE.getString(this)

fun @receiver:StringRes Int.stringRes(vararg args: Any?): String {
    return ArukuApplication.INSTANCE.getString(this, *args)
}