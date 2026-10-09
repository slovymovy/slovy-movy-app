package com.slovy.slovymovyapp.share

import android.content.Intent

/**
 * Extracts text the user sent to the app through an Android intent.
 *
 * - `ACTION_PROCESS_TEXT` is the text-selection toolbar entry ("OpenWords" next to Copy and
 *   Share). The selection arrives in `EXTRA_PROCESS_TEXT`. `EXTRA_PROCESS_TEXT_READONLY` only
 *   matters for apps that hand edited text back; OpenWords never returns a result, so both
 *   read-only and editable selections are handled the same way.
 * - `ACTION_SEND` with `text/plain` is the share sheet; the text is in `EXTRA_TEXT`.
 */
object AndroidSharedTextIntent {
    /** The shared text and its source, or null when [intent] is not a text hand-off. */
    fun read(intent: Intent?): Pair<String, SharedTextSource>? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                    ?.let { it to SharedTextSource.PROCESS_TEXT }
            Intent.ACTION_SEND ->
                if (intent.type?.startsWith("text/") == true) {
                    intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                        ?.let { it to SharedTextSource.SHARE }
                } else {
                    null
                }
            else -> null
        }
    }
}
