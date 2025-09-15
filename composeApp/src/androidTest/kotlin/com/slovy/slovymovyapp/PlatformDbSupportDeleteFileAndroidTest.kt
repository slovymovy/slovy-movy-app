package com.slovy.slovymovyapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlatformDbSupportDeleteFileAndroidTest {

    private val platform: PlatformDbSupport by lazy {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        PlatformDbSupport(ctx)
    }

    @Test
    fun delete_existing_file_twice_is_safe() {
        val path = platform.getDatabasePath("tmp_delete_test.db")
        // create file
        val out = platform.openOutput(path)
        out.write("hello".encodeToByteArray(), 0, 5)
        out.flush()
        out.close()
        assertTrue("File should exist before deletion", platform.fileExists(path))

        // delete 1st time
        platform.deleteFile(path)
        assertFalse("File should be deleted after first delete", platform.fileExists(path))

        // delete 2nd time — must be safe (no throw) and remain non-existent
        platform.deleteFile(path)
        assertFalse("File should remain absent after second delete", platform.fileExists(path))
    }

    @Test
    fun delete_nonexistent_file_twice_is_safe() {
        val path = platform.getDatabasePath("tmp_delete_missing.db")
        // Ensure missing
        if (platform.fileExists(path)) platform.deleteFile(path)
        assertFalse("Precondition: file should not exist", platform.fileExists(path))

        // Delete a missing file twice — must not throw
        platform.deleteFile(path)
        platform.deleteFile(path)
        assertFalse("File should still not exist after deletions", platform.fileExists(path))
    }
}
