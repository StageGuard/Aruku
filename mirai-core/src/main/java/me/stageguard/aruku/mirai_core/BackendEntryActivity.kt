package me.stageguard.aruku.mirai_core

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

@SuppressLint("CustomSplashScreen")
class BackendEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val result = startService(Intent(this, ArukuMiraiCoreService::class.java))
        Log.i("BackendEntryActivity", "service start result: $result")
        setResult(RESULT_OK, Intent().apply { putExtra("component", result) })
        finish()
    }
}