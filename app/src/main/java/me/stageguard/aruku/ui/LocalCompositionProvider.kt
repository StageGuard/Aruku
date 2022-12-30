package me.stageguard.aruku.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import com.google.accompanist.systemuicontroller.SystemUiController
import me.stageguard.aruku.service.IArukuMiraiInterface
import me.stageguard.aruku.util.StringLocale
import net.mamoe.mirai.Bot

val LocalArukuMiraiInterface: ProvidableCompositionLocal<IArukuMiraiInterface> =
    staticCompositionLocalOf { IArukuMiraiInterface.Default() }

val LocalMainNavProvider: ProvidableCompositionLocal<NavController> =
    staticCompositionLocalOf { error("No MainActivity NavController provided.") }

val LocalStringLocale: ProvidableCompositionLocal<StringLocale> =
    staticCompositionLocalOf { StringLocale(null) }

val LocalSystemUiController: ProvidableCompositionLocal<SystemUiController> =
    staticCompositionLocalOf { error("No SystemUiController provided.") }

val LocalBot: ProvidableCompositionLocal<Bot?> = compositionLocalOf { null }