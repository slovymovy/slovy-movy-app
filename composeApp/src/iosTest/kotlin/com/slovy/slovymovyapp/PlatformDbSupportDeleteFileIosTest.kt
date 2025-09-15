package com.slovy.slovymovyapp

import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformDbSupportDeleteFileIosTest {
    private val platform = PlatformDbSupport(null)

    @Test
    fun delete_existing_file_twice_is_safe() {
        val path = platform.getDatabasePath("tmp_delete_test.db")
        val out = platform.openOutput(path)
        out.write("hello".encodeToByteArray(), 0, 5)
        out.flush()
        out.close()
        assertTrue(platform.fileExists(path), "File should exist before deletion")

        platform.deleteFile(path)
        assertFalse(platform.fileExists(path), "File should be deleted after first delete")

        platform.deleteFile(path)
        assertFalse(platform.fileExists(path), "File should remain absent after second delete")
    }

    @Test
    fun delete_nonexistent_file_twice_is_safe() {
        val path = platform.getDatabasePath("tmp_delete_missing.db")
        if (platform.fileExists(path)) platform.deleteFile(path)
        assertFalse(platform.fileExists(path), "Precondition: file should not exist")

        platform.deleteFile(path)
        platform.deleteFile(path)
        assertFalse(platform.fileExists(path), "File should still not exist after deletions")
    }
}
