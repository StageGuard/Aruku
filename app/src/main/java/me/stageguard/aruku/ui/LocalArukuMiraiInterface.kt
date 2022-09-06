package me.stageguard.aruku.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import me.stageguard.aruku.service.IArukuMiraiInterface

val LocalArukuMiraiInterface: ProvidableCompositionLocal<IArukuMiraiInterface> =
    staticCompositionLocalOf { IArukuMiraiInterface.Default() }