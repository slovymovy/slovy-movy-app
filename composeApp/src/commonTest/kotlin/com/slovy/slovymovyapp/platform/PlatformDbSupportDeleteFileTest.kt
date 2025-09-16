package com.slovy.slovymovyapp.platform

import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.platformDbSupport
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformDbSupportDeleteFileTest : BaseTest() {

    @Test
    fun delete_existing_file_twice_is_safe() {
        val path = platformDbSupport().getDatabasePath("tmp_delete_test.db")
        // create file
        val out = platformDbSupport().openOutput(path)
        val buffer = "hello".encodeToByteArray()
        out.write(buffer, 0, buffer.size)
        out.flush()
        out.close()
        assertTrue(platformDbSupport().fileExists(path), "File should exist before deletion")

        // delete 1st time
        platformDbSupport().deleteFile(path)
        assertFalse(platformDbSupport().fileExists(path), "File should be deleted after first delete")

        // delete 2nd time — must be safe (no throw) and remain non-existent
        platformDbSupport().deleteFile(path)
        assertFalse(platformDbSupport().fileExists(path), "File should remain absent after second delete")
    }

    @Test
    fun delete_nonexistent_file_twice_is_safe() {
        val path = platformDbSupport().getDatabasePath("tmp_delete_missing.db")
        // Ensure missing
        if (platformDbSupport().fileExists(path)) platformDbSupport().deleteFile(path)
        assertFalse(platformDbSupport().fileExists(path), "Precondition: file should not exist")

        // Delete a missing file twice — must not throw
        platformDbSupport().deleteFile(path)
        platformDbSupport().deleteFile(path)
        assertFalse(platformDbSupport().fileExists(path), "File should still not exist after deletions")
    }
}