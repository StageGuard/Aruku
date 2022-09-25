@file:Suppress("ObjectPropertyName")

package me.stageguard.aruku.preference

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import com.heyanle.okkv2.core.Okkv
import com.heyanle.okkv2.core.okkv
import org.koin.android.ext.android.inject

object ArukuPreference : ComponentCallbacks2 {
    private val okkv: Okkv by inject()

    var activeBot by okkv.okkv<Long>("pref_active_bot")

    override fun onConfigurationChanged(newConfig: Configuration) {}
    override fun onLowMemory() {}
    override fun onTrimMemory(level: Int) {}
}