package me.stageguard.aruku.util

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import me.stageguard.aruku.ArukuApplication

/**
 * Created by LoliBall on 2022/9/6 17:36.
 * https://github.com/WhichWho
 */

val @receiver:StringRes Int.stringRes get() = ArukuApplication.INSTANCE.getString(this)

fun @receiver:StringRes Int.stringRes(vararg args: Any) = ArukuApplication.INSTANCE.getString(this, *args)
