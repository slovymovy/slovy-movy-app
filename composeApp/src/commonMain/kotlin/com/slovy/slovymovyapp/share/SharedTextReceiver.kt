package com.slovy.slovymovyapp.share

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Text handed to the app by the platform: an Android text-selection action, a share sheet,
 * or (later) an iOS share extension.
 *
 * [serial] addresses the request's passage when it opens in the Text Reader.
 */
data class SharedTextRequest(
    val text: String,
    val source: SharedTextSource,
    val serial: Long,
)

/** Where the platform got the text from; reported in analytics only. */
enum class SharedTextSource(val analyticsValue: String) {
    /** Android `ACTION_PROCESS_TEXT`: the user selected text in another app and picked OpenWords. */
    PROCESS_TEXT("process_text"),

    /** Android `ACTION_SEND` with `text/plain`: the share sheet. */
    SHARE("share"),
}

/**
 * Hands platform-delivered text to the composition. The platform calls [offer] from its entry
 * point (Android `onCreate`/`onNewIntent`); the app collects [requests]. Only the latest request
 * is buffered, so a share that cold-starts the app survives until `App` starts collecting.
 */
class SharedTextReceiver {
    private val channel = Channel<SharedTextRequest>(Channel.CONFLATED)

    val requests: Flow<SharedTextRequest> = channel.receiveAsFlow()

    private var nextSerial = 0L

    /** Ignores blank text; a later offer replaces an undelivered earlier one. */
    fun offer(text: String, source: SharedTextSource) {
        if (text.isBlank()) return
        channel.trySend(SharedTextRequest(text = text, source = source, serial = nextSerial++))
    }
}
