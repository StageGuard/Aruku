package me.stageguard.aruku.ui.page.chat

import android.net.Uri


sealed interface ChatAudioStatus {
    /**
     * audio is ready to play
     */
    class Ready(val waveLine: List<Double>) : ChatAudioStatus

    /**
     * audio is caching.
     */
    class Preparing(val progress: Double) : ChatAudioStatus

    /**
     * audio is not found.
     */
    object NotFound : ChatAudioStatus

    /**
     * error state.
     */
    class Error(val msg: String?) : ChatAudioStatus
}

sealed interface ChatFileStatus {
    /**
     * querying
     */
    object Querying : ChatFileStatus

    /**
     * file is ready to download.
     */
    class Operational(val url: String) : ChatFileStatus

    /**
     * file is expired that cannot be downloaded.
     */
    object Expired : ChatFileStatus

    /**
     * file is downloaded, ready to open.
     */
    class Ready(val uri: Uri) : ChatFileStatus

    /**
     * file is downloading.
     */
    class Downloading(val uri: Uri, val progress: Double) : ChatFileStatus

    /**
     * error state.
     */
    class Error(val msg: String?) : ChatFileStatus
}

sealed interface ChatQuoteMessageStatus {
    /**
     * querying quote message.
     */
    object Querying : ChatQuoteMessageStatus

    /**
     * query succeeded.
     */
    class Ready(val msg: ChatElement.Message) : ChatQuoteMessageStatus

    /**
     * error state.
     */
    class Error(val msg: String?) : ChatQuoteMessageStatus
}
