package me.stageguard.aruku.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.stageguard.aruku.common.createAndroidLogger
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.domain.RetrofitDownloadService
import me.stageguard.aruku.util.md5
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

class AudioCache(
    context: CoroutineContext,
    private val cacheFolder: File,
    private val database: ArukuDatabase,
    private val downloadService: RetrofitDownloadService
) : CoroutineScope {
    private val logger = createAndroidLogger()

    private val downloadJobs = ConcurrentHashMap<String, DownloadJob>() // audio md5
    private val stateListeners = ConcurrentHashMap<String, (State) -> Unit>() // audio md5
    private val listenerJobs = HashMap<String, Job>()

    override val coroutineContext: CoroutineContext = context + SupervisorJob()

    // only runs in main dispatcher
    @OptIn(ExperimentalCoroutinesApi::class)
    fun attachListener(fileMd5: String, listener: (State) -> Unit) {
        stateListeners[fileMd5] = listener

        val existing = listenerJobs[fileMd5]
        if (existing != null) {
            existing.cancel(CancellationException("cancelled manually"))
            listenerJobs.remove(fileMd5, existing)
        }

        val job = launch(context = Dispatchers.IO, start = CoroutineStart.LAZY) {
            runCatching {
                if (resolveCacheFile(fileMd5).md5.uppercase().contentEquals(fileMd5)) {
                    stateListeners[fileMd5]?.invoke(State.Ready)
                    return@launch
                }
            }.onFailure {
                if (it is FileNotFoundException && downloadJobs[fileMd5] == null) {
                    logger.i("observing audio $fileMd5 which is not cached, starting caching job.")
                    val url = database.suspendIO {
                        audioUrls().getAudioUrl(fileMd5).toList().singleOrNull()?.url
                    }
                    if(url != null) appendDownloadJob(fileMd5, url)
                    return@onFailure
                }

                throw it
            }

            while (isActive) { // cancel listener job when listener is detached.
                stateListeners[fileMd5]?.invoke(State.Preparing(0.0))

                val currDownloadJob = withContext(Dispatchers.IO) {
                    suspendCancellableCoroutine {
                        var downloadJob: DownloadJob? = downloadJobs[fileMd5]
                        while (downloadJob == null) { downloadJob = downloadJobs[fileMd5] }
                        it.resume(downloadJob, null)
                    }
                }

                val currListener = stateListeners[fileMd5]
                if (currListener != null) {
                    currListener(currDownloadJob.state.value)
                    currDownloadJob.state.collect { currListener(it) }
                }
            }
        }

        listenerJobs[fileMd5] = job
        job.start()
    }

    fun detachListener(fileMd5: String) {
        val listenerJob = listenerJobs[fileMd5]
        if (listenerJob != null) {
            listenerJob.cancel(CancellationException("audio listener is removed"))
            listenerJobs.remove(fileMd5, listenerJob)
        }

        val downloadJob = downloadJobs[fileMd5]
        if (downloadJob != null) {
            downloadJobs.remove(fileMd5, downloadJob)
        }

        stateListeners.remove(fileMd5)
    }

    fun appendDownloadJob(fileMd5: String, url: String) {
        val cacheFile = resolveCacheFile(fileMd5)

        val existing = downloadJobs[fileMd5]
        if (existing != null) {
            existing.job.cancel(CancellationException("cancelled manually"))
            downloadJobs.remove(fileMd5, existing)
            cacheFile.delete()
        }

        val stateFlow: MutableStateFlow<State> = MutableStateFlow(State.NotFound)

        val job = launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            if (!cacheFile.exists() || !cacheFile.md5.uppercase().contentEquals(fileMd5)) {
                cacheFile.parentFile?.mkdirs()
                cacheFile.createNewFile()
                stateFlow.update { State.Preparing(0.0) }
            }

            logger.i("caching audio $fileMd5.")
            val resp = downloadService.download(url)
            val body = resp.body() ?: kotlin.run {
                stateFlow.update { State.Error("body is null") }
                return@launch
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
                        stateFlow.update { State.Preparing(progress) }
                    }
                }
            }
            stateFlow.update { State.Ready }
        }

        downloadJobs[fileMd5] = DownloadJob(job, stateFlow.asStateFlow())
        job.invokeOnCompletion {
            if (it != null) {
                resolveCacheFile(fileMd5).delete()
                val errorState = State.Error(it.message)
                stateFlow.update { errorState }
            }
            logger.i("cache audio $fileMd5 complete.")
        }
        job.start()
        logger.i("audio $fileMd5 cache job is started.")
    }

    private fun resolveCacheFile(fileMd5: String): File =
        cacheFolder.resolve(fileMd5)


    sealed interface State {

        object Ready : State

        class Preparing(val progress: Double) : State

        object NotFound : State

        class Error(val msg: String? = null) : State
    }

    private class DownloadJob(
        val job: Job,
        val state: StateFlow<State>,
    )
}