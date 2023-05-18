package me.stageguard.aruku.ui.page.chat

import me.stageguard.aruku.R
import me.stageguard.aruku.util.stringRes



sealed interface UIMessageElement {
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
    }

    class AnnotatedText(val textSlice: List<Text>) : UIMessageElement

    sealed interface BaseImage : UIMessageElement
    class Image(
        val url: String?,
        val uuid: String,
        val width: Int,
        val height: Int,
        val isEmoticons: Boolean
    ) : BaseImage
    class FlashImage(
        val url: String?,
        val uuid: String,
        val width: Int,
        val height: Int,
    ) : BaseImage

    class Face(val id: Int) : BaseImage {
        companion object {
            val FACE_MAP = mapOf<Int, @receiver:DrawableRes Int>()
        }
    }
    class Audio(val identity: String, val name: String) : UIMessageElement
    class Forward(
        val preview: List<String>,
        val title: String,
        val brief: String,
        val summary: String,
        val nodes: List<me.stageguard.aruku.domain.data.message.Forward.Node>
    ) : UIMessageElement

    class Quote(val messageId: Long, ) : UIMessageElement

    class File(val name: String, val size: Long) : UIMessageElement

    // PokeMessage, VipFace, LightApp, MarketFace, SimpleServiceMessage, MusicShare, Dice, RockPaperScissors
    class Unsupported(val content: String) : UIMessageElement

    fun isImage() = this is Image || this is FlashImage
}