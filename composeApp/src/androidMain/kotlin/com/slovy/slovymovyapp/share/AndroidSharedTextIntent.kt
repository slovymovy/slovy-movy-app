package com.slovy.slovymovyapp.share

import android.content.Intent
import android.text.SpannableString
import android.text.style.URLSpan
import android.text.util.Linkify
import androidx.core.text.util.LinkifyCompat

/**
 * Extracts text the user sent to the app through an Android intent.
 *
 * - `ACTION_PROCESS_TEXT` is the text-selection toolbar entry ("OpenWords" next to Copy and
 *   Share). The selection arrives in `EXTRA_PROCESS_TEXT`. `EXTRA_PROCESS_TEXT_READONLY` only
 *   matters for apps that hand edited text back; OpenWords never returns a result, so both
 *   read-only and editable selections are handled the same way.
 * - `ACTION_SEND` with `text/plain` is the share sheet; the text is in `EXTRA_TEXT`. Senders
 *   compose that text themselves and usually add a link (a page URL, "Title https://…", a
 *   Chrome link-to-highlight), so web links are removed; they would otherwise be split into
 *   "words" like `https` and `www`.
 */
object AndroidSharedTextIntent {
    /** The shared text and its source, or null when [intent] is not a text hand-off. */
    fun read(intent: Intent): Pair<String, SharedTextSource>? = when (intent.action) {
        Intent.ACTION_PROCESS_TEXT ->
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.let { it.toString() to SharedTextSource.PROCESS_TEXT }
        Intent.ACTION_SEND ->
            if (intent.type?.startsWith("text/") == true) {
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.let { withoutWebLinks(it.toString()) to SharedTextSource.SHARE }
            } else {
                null
            }
        else -> null
    }

    /** [text] with every web link the platform's Linkify detects cut out, then trimmed. */
    private fun withoutWebLinks(text: String): String {
        val spannable = SpannableString(text)
        if (!LinkifyCompat.addLinks(spannable, Linkify.WEB_URLS)) return text
        val result = StringBuilder(text)
        spannable.getSpans(0, spannable.length, URLSpan::class.java)
            .sortedByDescending { spannable.getSpanStart(it) }
            .forEach { result.delete(spannable.getSpanStart(it), spannable.getSpanEnd(it)) }
        return result.toString().trim()
    }
}
