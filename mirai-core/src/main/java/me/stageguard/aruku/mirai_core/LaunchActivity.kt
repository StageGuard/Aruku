package me.stageguard.aruku.mirai_core

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log

@SuppressLint("CustomSplashScreen")
class LaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)

        println("referer: $referrer")
        val result = startService(Intent(this, ArukuMiraiCoreService::class.java))
        Log.i("ArukuMiraiCoreService", "service start result: $result")
        setResult(RESULT_OK, Intent().apply { putExtra("component", result) })
        finish()
    }
}