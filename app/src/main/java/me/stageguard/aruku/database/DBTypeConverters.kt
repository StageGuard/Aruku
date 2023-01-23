package me.stageguard.aruku.database

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.protobuf.ProtoBuf
import me.stageguard.aruku.domain.data.message.*

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