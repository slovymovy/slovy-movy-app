package com.slovy.slovymovyapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlatformDbSupportAvailableBytesAndroidTest {

    private val platform: PlatformDbSupport by lazy {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        PlatformDbSupport(ctx)
    }

    @Test
    fun available_bytes_for_db_dir_is_positive() {
        val path = platform.getDatabasePath("tmp_avail_test.db")
        val bytes = platform.getAvailableBytesForPath(path)
        assertNotNull("Available bytes should not be null for a valid path: $path", bytes)
        assertTrue("Available bytes should be greater than 0, was $bytes", bytes!! > 0)
    }
}
