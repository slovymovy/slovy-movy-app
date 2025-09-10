package com.slovy.slovymovyapp

import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DataDbManagerDesktopTest {
    @Test
    fun download_and_open_readonly() {
        val platform = PlatformDbSupport(null)
        val mgr = DataDbManager(platform, null)

        // ensure files (use smallest remote DBs)
        val dict = runBlocking { mgr.ensureDictionary("en") }
        val tr = runBlocking { mgr.ensureTranslation("nl", "en") }

        assertTrue(File(dict.path).exists(), "Dictionary file should exist: ${dict.path}")
        assertTrue(File(tr.path).exists(), "Translation file should exist: ${tr.path}")

        // open read-only — should not throw
        mgr.openTranslationReadOnly("nl", "en")
        mgr.openDictionaryReadOnly("en")
    }
}
