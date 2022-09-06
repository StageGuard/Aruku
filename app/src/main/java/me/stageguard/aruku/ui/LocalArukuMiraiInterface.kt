package me.stageguard.aruku.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import me.stageguard.aruku.service.IArukuMiraiInterface

val LocalArukuMiraiInterface: ProvidableCompositionLocal<IArukuMiraiInterface> =
    staticCompositionLocalOf { IArukuMiraiInterface.Default() }

val LocalNavController = staticCompositionLocalOf<NavController> { error("Non Application Nav") }