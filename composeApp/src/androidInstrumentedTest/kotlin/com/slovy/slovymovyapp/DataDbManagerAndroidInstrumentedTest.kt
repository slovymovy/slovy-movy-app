package com.slovy.slovymovyapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DataDbManagerAndroidInstrumentedTest {
    @Test
    fun download_and_open_readonly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val platform = PlatformDbSupport(context)
        val mgr = DataDbManager(platform, null)

        // ensure files (use smallest remote DBs)
        val dict = runBlocking { mgr.ensureDictionary("en") }
        val tr = runBlocking { mgr.ensureTranslation("nl", "en") }

        assertTrue("Dictionary file should exist: ${dict.path}", File(dict.path).exists())
        assertTrue("Translation file should exist: ${tr.path}", File(tr.path).exists())

        // open read-only — should not throw
        mgr.openTranslationReadOnly("nl", "en")
        mgr.openDictionaryReadOnly("en")
    }
}
