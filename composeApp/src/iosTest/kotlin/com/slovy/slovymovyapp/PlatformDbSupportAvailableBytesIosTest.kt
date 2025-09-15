package com.slovy.slovymovyapp

import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlatformDbSupportAvailableBytesIosTest {
    private val platform = PlatformDbSupport(null)

    @Test
    fun available_bytes_for_db_dir_is_positive() {
        val path = platform.getDatabasePath("tmp_avail_test.db")
        val bytes = platform.getAvailableBytesForPath(path)
        assertNotNull(bytes, "Available bytes should not be null for a valid path: $path")
        assertTrue(bytes > 0, "Available bytes should be greater than 0, was $bytes")
    }
}
