package me.stageguard.aruku.domain.data.message

import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class Poke(
    val name: String,
    val type: Int,
    val id: Int
) : MessageElement {
    override fun contentToString(): String {
        return buildString {
            append("[")
            append(values[type to id]?.name ?: "戳一戳")
            append("]")
        }
    }

    companion object {
        val values = buildMap {
            set(1 to -1, Poke("戳一戳", 1, -1))
            set(2 to -1, Poke("比心", 2, -1))
            set(3 to -1, Poke("点赞", 3, -1))
            set(4 to -1, Poke("心碎", 4, -1))
            set(5 to -1, Poke("666", 5, -1))
            set(6 to -1, Poke("放大招", 6, -1))
            set(126 to 2011, Poke("宝贝球", 126, 2011))
            set(126 to 2007, Poke("玫瑰花", 126, 2007))
            set(126 to 2006, Poke("召唤术", 126, 2006))
            set(126 to 2009, Poke("让你皮", 126, 2009))
            set(126 to 2005, Poke("结印", 126, 2005))
            set(126 to 2004, Poke("手雷", 126, 2004))
            set(126 to 2003, Poke("勾引", 126, 2003))
            set(126 to 2001, Poke("抓一下", 126, 2001))
            set(126 to 2002, Poke("碎屏", 126, 2002))
            set(126 to 2000, Poke("敲门", 126, 2000))
        }
    }
}

@Parcelize
@Serializable
data class VipFace(
    val id: Int,
    val name: String,
    val count: Int,
) : MessageElement {
    override fun contentToString(): String {
        return "[VIP表情]${names[id] ?: "表情"}x${count}"
    }

    companion object {
        val names = mapOf(
            9 to "榴莲",
            1 to "平底锅",
            12 to "钞票",
            10 to "略略略",
            4 to "猪头",
            6 to "便便",
            5 to "炸弹",
            2 to "爱心",
            3 to "哈哈",
            1 to "点赞",
            7 to "亲亲",
            8 to "药丸"
        )
    }
}

@Parcelize
@Serializable
data class Dice(
    val value: Int,
) : MessageElement {
    override fun contentToString(): String {
        return "[骰子]"
    }
}

@Parcelize
@Serializable
data class RPS(
    private val id: Int,
    val name: String,
) : MessageElement {
    override fun contentToString(): String {
        return "[$name]"
    }

    companion object {
        val values = mutableMapOf(
            48 to RPS(48, "石头"),
            49 to RPS(49, "剪刀"),
            50 to RPS(50, "布")
        )
    }
}

@Parcelize
@Serializable
data class MarketFace(
    val id: Int,
    val name: String
) : MessageElement {
    override fun contentToString(): String {
        return name
    }
}

@Parcelize
@Serializable
data class FlashImage(
    val url: String,
    val uuid: String,
    val width: Int,
    val height: Int,
) : MessageElement {
    override fun contentToString(): String {
        return "[闪照]"
    }
}