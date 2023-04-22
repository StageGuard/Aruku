package me.stageguard.aruku.cache

import android.os.Parcelable
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import me.stageguard.aruku.domain.RetrofitDownloadService
import me.stageguard.aruku.service.parcel.AudioStatusListener
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

class AudioCache(
    context: CoroutineContext,
    private val cacheFolder: File,
    private val downloadService: RetrofitDownloadService
) : CoroutineScope {
    private val downloadJobs = ConcurrentHashMap<String, DownloadJob>() // audio md5
    private val stateListeners = ConcurrentHashMap<String, AudioStatusListener>() // audio md5
    private val listenerJobs = HashMap<String, Job>()

    override val coroutineContext: CoroutineContext = context + SupervisorJob()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun attachListener(fileMd5: String, listener: AudioStatusListener) {
        stateListeners[fileMd5] = listener

        val job = launch(start = CoroutineStart.LAZY) {
            while (isActive) { // cancel listener job when listener is detached.
                val currDownloadJob = suspendCancellableCoroutine {
                    var downloadJob: DownloadJob? = null
                    while (downloadJob == null) { downloadJob = downloadJobs[fileMd5] }
                    it.resume(downloadJob, null)
                }
                val currListener = stateListeners[fileMd5]

                if (currListener != null) {
                    withContext(Dispatchers.Main) { currListener.onState(currDownloadJob.state.value) }
                    currDownloadJob.stateChannel.consumeAsFlow().cancellable().collect {
                        withContext(Dispatchers.Main) { currListener.onState(it) }
                    }
                }

            }
        }

        listenerJobs.putIfAbsent(fileMd5, job)
        job.start()
    }

    fun detachListener(fileMd5: String) {
        val job = listenerJobs[fileMd5]
        if (job != null) {
            job.cancel(CancellationException("audio listener is removed"))
            listenerJobs.remove(fileMd5, job)
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

        val channel = Channel<State>()
        val atomState = atomic<State>(State.NotFound)

        suspend fun set(resolve: State) {
            channel.send(resolve)
            atomState.value = resolve
        }

        val job = launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            if (!cacheFile.exists()) {
                cacheFile.parentFile?.mkdirs()
                cacheFile.createNewFile()
                set(State.Preparing(0.0))
            }

            val resp = downloadService.download(url)
            val body = resp.body() ?: kotlin.run {
                set(State.Error("body is null"))
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
                        set(State.Preparing(progress))
                    }
                }
            }
            set(State.Ready)
        }

        downloadJobs[fileMd5] = DownloadJob(job, atomState, channel)
        job.invokeOnCompletion {
            if (it != null) {
                resolveCacheFile(fileMd5).delete()
                val errorState = State.Error(it.message)
                channel.trySend(errorState)
                atomState.value = errorState
            }
            channel.close()
            downloadJobs.remove(fileMd5)
        }
        job.start()
    }

    private fun resolveCacheFile(fileMd5: String): File =
        cacheFolder.resolve(fileMd5)


    sealed interface State {
        @Parcelize
        object Ready : State, Parcelable
        @Parcelize
        class Preparing(val progress: Double) : State, Parcelable
        @Parcelize
        object NotFound : State, Parcelable
        @Parcelize
        class Error(val msg: String? = null) : State, Parcelable
    }

    private class DownloadJob(
        val job: Job,
        val state: AtomicRef<State>,
        val stateChannel: ReceiveChannel<State>
    )
}