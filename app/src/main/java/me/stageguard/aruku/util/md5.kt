package me.stageguard.aruku.util

import java.io.File
import java.math.BigInteger
import java.security.MessageDigest


val File.md5
    get() = run {
        val data: ByteArray = readBytes()
        val hash = MessageDigest.getInstance("MD5").digest(data)
        BigInteger(1, hash).toString(16)
    }

public fun ByteArray.hex(
    separator: String = "",
    offset: Int = 0,
    length: Int = this.size - offset
): String {
    if (length == 0) {
        return ""
    }
    val lastIndex = offset + length
    return buildString(length * 2) {
        this@hex.forEachIndexed { index, it ->
            if (index in offset until lastIndex) {
                val ret = it.toUByte().toString(16).uppercase()
                if (ret.length == 1) append('0')
                append(ret)
                if (index < lastIndex - 1) append(separator)
            }
        }
    }
}