package me.stageguard.aruku.util.apng

import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.decode.AssetMetadata
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.decode.ImageSource
import coil.fetch.SourceResult
import coil.request.Options
import com.github.penfeizhou.animation.apng.APNGDrawable
import com.github.penfeizhou.animation.apng.decode.APNGParser
import me.stageguard.aruku.ArukuApplication

class AnimatedPngDecoder(private val source: ImageSource) : Decoder {
    override suspend fun decode(): DecodeResult {
        return DecodeResult(
            drawable = APNGDrawable.fromAsset(ArukuApplication.INSTANCE, source.file().toString()),
            isSampled = false
        )
    }

    class Factory : Decoder.Factory {
        @OptIn(ExperimentalCoilApi::class)
        override fun create(
            result: SourceResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
            val fromAsset = result.source.metadata is AssetMetadata
            val isApng =
                APNGParser.isAPNG(ArukuApplication.INSTANCE, result.source.file().toString())
            return if (fromAsset && isApng) AnimatedPngDecoder(result.source) else null
        }
    }
}