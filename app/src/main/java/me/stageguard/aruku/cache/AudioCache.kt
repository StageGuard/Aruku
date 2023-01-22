package me.stageguard.aruku.cache

import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import me.stageguard.aruku.domain.RetrofitDownloadService
import me.stageguard.aruku.service.parcel.ArukuAudio
import net.mamoe.mirai.utils.ConcurrentHashMap
import net.mamoe.mirai.utils.toUHexString
import java.io.File

class AudioCache(
    private val cacheFolder: File,
    private val downloadService: RetrofitDownloadService
) {
    private val downloadJobs = ConcurrentHashMap<ArukuAudio, DownloadJob>()

    fun resolve(audio: ArukuAudio): ResolveResult {
        val cacheFile = resolveCacheFile(audio)

        if (!cacheFile.exists() || !cacheFile.isFile) {
            return ResolveResult.NotFound(cacheFile)
        }

        val job = downloadJobs[audio]
        return if (job != null) {
            job.liveData.value ?: ResolveResult.Preparing(cacheFile, job.progress)
        } else {
            ResolveResult.Ready(cacheFile)
        }
    }

    fun resolveAsFlow(audio: ArukuAudio) = flow {
        val cacheFile = resolveCacheFile(audio)

        if (!cacheFile.exists() || !cacheFile.isFile) {
            emit(ResolveResult.NotFound(cacheFile))
        }

        var job = downloadJobs[audio]
        while (job != null && job.job.isActive) {
            emit(job.liveData.value ?: ResolveResult.Preparing(cacheFile, job.progress))
            job = downloadJobs[audio]
        }
    }

    fun appendDownloadJob(scope: CoroutineScope, audio: ArukuAudio, url: String) {
        val event = MutableLiveData<ResolveResult>()
        val job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            val selfJob = downloadJobs[audio]

            val resp = downloadService.download(url)
            val body = resp.body() ?: return@launch

            val cacheFile = resolveCacheFile(audio)
            if (!cacheFile.exists()) {
                cacheFile.parentFile?.mkdirs()
                cacheFile.createNewFile()
                selfJob?.progress = 0.0
                event.postValue(ResolveResult.Preparing(cacheFile, 0.0))
            }
            body.byteStream().use { byteStream ->
                cacheFile.outputStream().use { outputStream ->
                    val totalBytes = body.contentLength()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var progressBytes = 0L
                    var bytes = byteStream.read(buffer)
                    while (bytes >= 0) {
                        outputStream.write(buffer, 0, bytes)
                        progressBytes += bytes
                        bytes = byteStream.read(buffer)

                        val progress = progressBytes / totalBytes.toDouble()
                        selfJob?.progress = progress
                        event.postValue(ResolveResult.Preparing(cacheFile, progress))
                    }
                }
            }
            selfJob?.progress = 1.0
            event.postValue(ResolveResult.Ready(cacheFile))
        }

        val existing = downloadJobs[audio]
        if (existing != null) {
            existing.job.cancel("cancelled manually", null)
            existing.liveData.postValue(ResolveResult.NotFound(resolveCacheFile(audio)))
        }

        downloadJobs[audio] = DownloadJob(job, 0.0, event)
        job.invokeOnCompletion {
            if (it != null) resolveCacheFile(audio).delete()
            downloadJobs.remove(audio)
        }
        job.start()
    }

    private fun resolveCacheFile(audio: ArukuAudio): File =
        cacheFolder.resolve("${audio.filename}_${audio.fileMd5.toUHexString()}.${audio.extension}")


    sealed class ResolveResult(val file: File) {
        class Ready(file: File) : ResolveResult(file)
        class Preparing(file: File, val progress: Double) : ResolveResult(file)
        class NotFound(file: File) : ResolveResult(file)
    }

    inner class DownloadJob(
        val job: Job,
        var progress: Double,
        val liveData: MutableLiveData<ResolveResult>
    )
}