package me.stageguard.aruku.util

import coil.ImageLoader
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.decode.ImageSource
import coil.fetch.SourceResult
import coil.request.Options
import com.github.penfeizhou.animation.apng.APNGDrawable
import com.github.penfeizhou.animation.loader.AssetStreamLoader
import me.stageguard.aruku.ArukuApplication

class FaceAPNGDecoder(private val source: ImageSource) : Decoder {

    override suspend fun decode(): DecodeResult {
        val assetPath = source.file().toString().substringAfter("android_asset/")
        val assetLoader = AssetStreamLoader(ArukuApplication.INSTANCE, assetPath)

        return DecodeResult(
            drawable = APNGDrawable(assetLoader),
            isSampled = false
        )
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
            val rawPath = result.source.file().toString()
            if (!rawPath.matches(faceAssetMatcher)) return null

            return FaceAPNGDecoder(result.source)
        }

        companion object {
            private val faceAssetMatcher = Regex("/file:/android_asset/face/face_(\\d{3})\\.apng")
        }
    }
}