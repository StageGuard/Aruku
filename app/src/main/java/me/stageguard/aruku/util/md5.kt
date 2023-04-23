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