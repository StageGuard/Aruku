package me.stageguard.aruku.database

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.protobuf.ProtoBuf
import me.stageguard.aruku.common.message.At
import me.stageguard.aruku.common.message.AtAll
import me.stageguard.aruku.common.message.Audio
import me.stageguard.aruku.common.message.Dice
import me.stageguard.aruku.common.message.Face
import me.stageguard.aruku.common.message.File
import me.stageguard.aruku.common.message.FlashImage
import me.stageguard.aruku.common.message.Forward
import me.stageguard.aruku.common.message.Image
import me.stageguard.aruku.common.message.MarketFace
import me.stageguard.aruku.common.message.MessageElement
import me.stageguard.aruku.common.message.PlainText
import me.stageguard.aruku.common.message.Poke
import me.stageguard.aruku.common.message.Quote
import me.stageguard.aruku.common.message.RPS
import me.stageguard.aruku.common.message.VipFace

@ProvidedTypeConverter
@OptIn(ExperimentalSerializationApi::class)
class DBTypeConverters {
    @TypeConverter
    fun fromEncodedData(value: String): List<MessageElement> {
        return protobuf.decodeFromHexString(value)
    }

    @TypeConverter
    fun encodeToData(value: List<MessageElement>): String {
        return protobuf.encodeToHexString(value)
    }

    companion object {
        private val protobuf = ProtoBuf {
            serializersModule = SerializersModule {
                polymorphic(MessageElement::class) {
                    subclass(At::class, At.serializer())
                    subclass(AtAll::class, AtAll.serializer())
                    subclass(Audio::class, Audio.serializer())
                    subclass(Dice::class, Dice.serializer())
                    subclass(Face::class, Face.serializer())
                    subclass(File::class, File.serializer())
                    subclass(FlashImage::class, FlashImage.serializer())
                    subclass(Forward::class, Forward.serializer())
                    subclass(Image::class, Image.serializer())
                    subclass(MarketFace::class, MarketFace.serializer())
                    subclass(PlainText::class, PlainText.serializer())
                    subclass(Poke::class, Poke.serializer())
                    subclass(Quote::class, Quote.serializer())
                    subclass(RPS::class, RPS.serializer())
                    subclass(VipFace::class, VipFace.serializer())
                }
            }
        }
    }
}