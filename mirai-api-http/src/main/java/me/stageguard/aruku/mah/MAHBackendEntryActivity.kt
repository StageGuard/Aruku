package me.stageguard.aruku.mah

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

@SuppressLint("CustomSplashScreen")
class MAHBackendEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val result = startService(Intent(this, ArukuMiraiApiHttpService::class.java))
        Log.i("MAHBackendEntryActivity", "service start result: $result")
        setResult(RESULT_OK, Intent().apply { putExtra("component", result) })
        finish()
    }
}