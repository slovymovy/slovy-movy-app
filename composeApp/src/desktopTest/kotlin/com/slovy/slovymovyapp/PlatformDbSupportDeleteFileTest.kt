package com.slovy.slovymovyapp

import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformDbSupportDeleteFileTest {
    private val platform = PlatformDbSupport(null)

    @Test
    fun delete_existing_file_twice_is_safe() {
        val path = platform.getDatabasePath("tmp_delete_test.db")
        // create file
        val out = platform.openOutput(path)
        out.write("hello".encodeToByteArray(), 0, 5)
        out.flush()
        out.close()
        assertTrue(platform.fileExists(path), "File should exist before deletion")

        // delete 1st time
        platform.deleteFile(path)
        assertFalse(platform.fileExists(path), "File should be deleted after first delete")

        // delete 2nd time — must be safe (no throw) and remain non-existent
        platform.deleteFile(path)
        assertFalse(platform.fileExists(path), "File should remain absent after second delete")
    }

    @Test
    fun delete_nonexistent_file_twice_is_safe() {
        val path = platform.getDatabasePath("tmp_delete_missing.db")
        // Ensure missing
        if (platform.fileExists(path)) platform.deleteFile(path)
        assertFalse(platform.fileExists(path), "Precondition: file should not exist")

        // Delete a missing file twice — must not throw
        platform.deleteFile(path)
        platform.deleteFile(path)
        assertFalse(platform.fileExists(path), "File should still not exist after deletions")
    }
}
