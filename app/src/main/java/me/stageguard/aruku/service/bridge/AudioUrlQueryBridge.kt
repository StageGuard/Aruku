package me.stageguard.aruku.service.bridge

import remoter.annotations.Remoter

@Remoter
interface AudioUrlQueryBridge {
    fun query(fileMd5: String): String?
}