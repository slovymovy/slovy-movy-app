package com.slovy.slovymovyapp.share

import android.content.Intent
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class AndroidSharedTextIntentTest {

    @Test
    fun readsProcessTextSelection() {
        val intent = Intent(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT, "gezellig")
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        assertEquals("gezellig" to SharedTextSource.PROCESS_TEXT, AndroidSharedTextIntent.read(intent))
    }

    @Test
    fun readsSharedPlainText() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "De stad was stil.")
        assertEquals("De stad was stil." to SharedTextSource.SHARE, AndroidSharedTextIntent.read(intent))
    }

    @Test
    fun removesWebLinksFromSharedText() {
        fun share(text: String) = AndroidSharedTextIntent.read(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        )
        assertEquals(
            "Die Rente steigt" to SharedTextSource.SHARE,
            share("Die Rente steigt https://www.spiegel.de/politik/rente-a-1234"),
            "a trailing page link must be cut off"
        )
        assertEquals(
            "“gezellig”" to SharedTextSource.SHARE,
            share("“gezellig” https://example.nl/artikel#:~:text=gezellig"),
            "a Chrome link-to-highlight must be cut off and leave the quoted selection"
        )
        assertEquals("" to SharedTextSource.SHARE, share("https://example.com/page"), "a bare link leaves no text")
        assertEquals(
            "PROCESS_TEXT keeps https://example.com" to SharedTextSource.PROCESS_TEXT,
            AndroidSharedTextIntent.read(
                Intent(Intent.ACTION_PROCESS_TEXT)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_PROCESS_TEXT, "PROCESS_TEXT keeps https://example.com")
            ),
            "a selection is exactly what the user chose and must be left untouched"
        )
    }

    @Test
    fun ignoresLauncherAndNonTextIntents() {
        assertNull(AndroidSharedTextIntent.read(Intent(Intent.ACTION_MAIN)), "the launcher intent carries no text")
        val imageShare = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_TEXT, "caption")
        assertNull(AndroidSharedTextIntent.read(imageShare), "a non-text share must be ignored")
        val emptyProcessText = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
        assertNull(AndroidSharedTextIntent.read(emptyProcessText), "a selection without text must be ignored")
    }
}
