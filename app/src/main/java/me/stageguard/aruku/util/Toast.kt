package me.stageguard.aruku.util

import android.content.Context
import android.widget.Toast

fun Context.toastShort(content: String) {
    Toast.makeText(this, content, Toast.LENGTH_SHORT).show()
}