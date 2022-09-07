package me.stageguard.aruku.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import net.mamoe.mirai.utils.getRandomIntString
import net.mamoe.mirai.utils.getRandomString
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.random.Random

private val DEFAULT_DEVICE_ID by lazy { "86${getRandomIntString(12, Random)}".let { it + luhn(it) } }

val Context.imei
    @SuppressLint("HardwareIds", "MissingPermission")
    get() = (getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?).run {
        if (isReadPhoneStatePermissionGranted()) {
            this?.imei ?: DEFAULT_DEVICE_ID
        } else DEFAULT_DEVICE_ID
    }

private fun Context.isReadPhoneStatePermissionGranted() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED


private fun luhn(imei: String): Int {
    var odd = false
    val zero = '0'
    val sum = imei.sumOf { char ->
        odd = !odd
        if (odd) {
            char.code - zero.code
        } else {
            val s = (char.code - zero.code) * 2
            s % 10 + s / 10
        }
    }
    return (10 - sum % 10) % 10
}

val deviceKernelVersion by lazy {
    try {
        val p = Runtime.getRuntime().exec("uname -a")
        val inStream: InputStream = if (p.waitFor() == 0) p.inputStream else p.errorStream
        val br = BufferedReader(InputStreamReader(inStream), 1024)
        val line: String = br.readLine()
        br.close()
        line
    } catch (ex: Exception) {
        "Linux version 3.0.31-${getRandomString(8, Random.Default)} (android-build@xxx.xxx.xxx.xxx.com)"
    }
}