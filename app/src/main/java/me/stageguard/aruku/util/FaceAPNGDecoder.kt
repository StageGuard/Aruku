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
        private val faceList by lazy { ArukuApplication.INSTANCE.assets.list("face/") }
        private val existTable: MutableMap<String, Boolean> = hashMapOf()

        override fun create(
            result: SourceResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
            val rawPath = result.source.file().toString()
            if (!rawPath.matches(faceAssetMatcher)) return null
            val list = faceList ?: return null

            val faceFileName = rawPath.substringAfter("face/")

            // fast path
            val exist = existTable[faceFileName]
            if (exist == true) return FaceAPNGDecoder(result.source)
            if (exist == false) return null

            // slow path
            val contains = list.contains(faceFileName)
            existTable[faceFileName] = contains
            return if (contains) FaceAPNGDecoder(result.source) else null
        }

        companion object {
            private val faceAssetMatcher = Regex("/file:/android_asset/face/face_(\\d{3})\\.apng")
        }
    }
}