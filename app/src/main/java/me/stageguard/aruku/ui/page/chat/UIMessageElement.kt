package me.stageguard.aruku.ui.page.chat

import me.stageguard.aruku.R
import me.stageguard.aruku.util.stringRes



sealed interface UIMessageElement {
    fun contentToString(): String

    sealed interface Text {
        val text: String

        class PlainText(val content: String) : Text {
            override val text: String = content
        }
        class At(val targetId: Long, val targetName: String) : Text {
            override val text: String = targetName
        }
        object AtAll : Text {
            override val text: String = R.string.message_at_all.stringRes
        }
        class Face(val id: Int, val name: String) : Text {
            override val text: String
                get() = "[$name]"
        }
    }

    class AnnotatedText(val textSlice: List<Text>) : UIMessageElement {
        override fun contentToString() = buildString {
            append(*textSlice.map(Text::text).toTypedArray())
        }
    }

    sealed interface BaseImage : UIMessageElement
    class Image(
        val url: String?,
        val uuid: String,
        val width: Int,
        val height: Int,
        val isEmoticons: Boolean
    ) : BaseImage {
        override fun contentToString(): String {
            return "[图片]"
        }
    }
    class FlashImage(
        val url: String?,
        val uuid: String,
        val width: Int,
        val height: Int,
    ) : BaseImage {
        override fun contentToString(): String {
            return "[闪照]"
        }
    }

    class Audio(val fileMd5: String, val name: String) : UIMessageElement {
        override fun contentToString(): String {
            return "[语音]"
        }
    }
    class Forward(
        val preview: List<String>,
        val title: String,
        val brief: String,
        val summary: String,
        val nodes: List<me.stageguard.aruku.common.message.Forward.Node>
    ) : UIMessageElement {
        override fun contentToString(): String {
            return "[转发消息]"
        }
    }

    class Quote(val messageId: Long, ) : UIMessageElement {
        override fun contentToString(): String {
            return ""
        }
    }

    class File(
        val id: String?,
        val name: String,
        val extension: String?,
        val size: Long,
        val correspondingMessageId: Long, // used to update db message.
    ) : UIMessageElement {
        override fun contentToString(): String {
            return "[文件]"
        }
    }

    // PokeMessage, VipFace, LightApp, MarketFace, SimpleServiceMessage, MusicShare, Dice, RockPaperScissors
    class Unsupported(val content: String) : UIMessageElement {
        override fun contentToString(): String {
            return "[未知]"
        }
    }

    fun isImage() = this is Image || this is FlashImage
}

fun List<UIMessageElement>.contentToString() = buildString {
    append(*map(UIMessageElement::contentToString).toTypedArray())
}