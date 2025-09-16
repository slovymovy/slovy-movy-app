package com.slovy.slovymovyapp.platform

import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.platformDbSupport
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlatformDbSupportAvailableBytesTest : BaseTest() {

    @Test
    fun available_bytes_for_db_dir_is_positive() {
        val path = platformDbSupport().getDatabasePath("tmp_avail_test.db")
        val bytes = platformDbSupport().getAvailableBytesForPath(path)
        assertNotNull(bytes, "Available bytes should not be null for a valid path: $path")
        assertTrue(bytes > 0, "Available bytes should be greater than 0, was $bytes")
    }
}