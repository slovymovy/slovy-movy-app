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
    fun ignoresLauncherAndNonTextIntents() {
        assertNull(AndroidSharedTextIntent.read(null), "a null intent carries no text")
        assertNull(AndroidSharedTextIntent.read(Intent(Intent.ACTION_MAIN)), "the launcher intent carries no text")
        val imageShare = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_TEXT, "caption")
        assertNull(AndroidSharedTextIntent.read(imageShare), "a non-text share must be ignored")
        val emptyProcessText = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
        assertNull(AndroidSharedTextIntent.read(emptyProcessText), "a selection without text must be ignored")
    }
}
