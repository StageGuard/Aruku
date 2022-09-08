package me.stageguard.aruku.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import me.stageguard.aruku.service.IArukuMiraiInterface
import me.stageguard.aruku.util.StringResource

val LocalArukuMiraiInterface: ProvidableCompositionLocal<IArukuMiraiInterface> =
    staticCompositionLocalOf { IArukuMiraiInterface.Default() }

val LocalMainNavProvider: ProvidableCompositionLocal<NavController> =
    staticCompositionLocalOf { error("No MainActivity NavController provided.") }

val LocalStringRes: ProvidableCompositionLocal<StringResource> =
    staticCompositionLocalOf { StringResource(null) }