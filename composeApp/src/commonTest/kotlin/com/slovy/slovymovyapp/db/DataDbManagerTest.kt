package com.slovy.slovymovyapp.db

import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.platformDbSupport
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue


class DataDbManagerTest : BaseTest() {
    @Test
    fun download_and_open_readonly() {
        val platform = platformDbSupport()
        val mgr = DataDbManager(platform, null)

        runBlocking {
            mgr.deleteDictionary("en")
            mgr.deleteTranslation(
                src = "nl",
                tgt = "en"
            )
        }

        // ensure files (use smallest remote DBs)
        val dict = runBlocking { mgr.ensureDictionary("en") }
        val tr = runBlocking { mgr.ensureTranslation("nl", "en") }

        try {
            assertTrue(platform.fileExists(dict), "Dictionary file should exist: $dict")
            assertTrue(platform.fileExists(tr), "Translation file should exist: $tr")

            // open read-only — should not throw
            mgr.openTranslationReadOnly("nl", "en")
            mgr.openDictionaryReadOnly("en")
        } finally {
            runBlocking {
                mgr.deleteDictionary("en")
                mgr.deleteTranslation(
                    src = "nl",
                    tgt = "en"
                )
            }
        }
    }


}