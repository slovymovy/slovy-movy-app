package com.slovy.slovymovyapp

import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class DataDbManagerIosTest {
    @Test
    fun download_and_open_readonly() {
        val platform = PlatformDbSupport(null)
        val mgr = DataDbManager(platform, null)

        // ensure files (use smallest remote DBs)
        val dict = runBlocking { mgr.ensureDictionary("en") }
        val tr = runBlocking { mgr.ensureTranslation("nl", "en") }

        assertTrue(dict.path.isNotBlank(), "Dictionary path should not be blank")
        assertTrue(tr.path.isNotBlank(), "Translation path should not be blank")

        // open read-only — should not throw
        mgr.openTranslationReadOnly("nl", "en")
        mgr.openDictionaryReadOnly("en")
    }
}
