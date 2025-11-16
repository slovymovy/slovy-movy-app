package com.slovy.slovymovyapp.platform

import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.testPlatformDbSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlatformDbSupportFileSizeTest : BaseTest() {

    @Test
    fun getFileSize_returns_null_for_nonexistent_file() {
        val path = testPlatformDbSupport().getDatabasePath("nonexistent_file.db")
        val size = testPlatformDbSupport().getFileSize(path)
        assertNull(size, "File size should be null for nonexistent file")
    }

    @Test
    fun getFileSize_returns_correct_size_for_existing_file() {
        val support = testPlatformDbSupport()
        val path = support.getDatabasePath("test_file_size.db")

        // Create file with known content
        support.ensureDatabasesDir()
        val testData = "Hello, World!".encodeToByteArray()
        val output = support.openOutput(path)
        output.write(testData, 0, testData.size)
        output.flush()
        output.close()

        // Verify size
        val size = support.getFileSize(path)
        assertNotNull(size, "File size should not be null for existing file")
        assertEquals(testData.size.toLong(), size, "File size should match written data size")

        // Cleanup
        support.deleteFile(path)
    }

    @Test
    fun getFileSize_returns_zero_for_empty_file() {
        val support = testPlatformDbSupport()
        val path = support.getDatabasePath("test_empty_file.db")

        // Create empty file
        support.ensureDatabasesDir()
        val output = support.openOutput(path)
        output.close()

        // Verify size
        val size = support.getFileSize(path)
        assertNotNull(size, "File size should not be null for empty file")
        assertEquals(0L, size, "Empty file should have size 0")

        // Cleanup
        support.deleteFile(path)
    }

    @Test
    fun getFileSize_handles_larger_files() {
        val support = testPlatformDbSupport()
        val path = support.getDatabasePath("test_large_file.db")

        // Create file with larger content (10 KB)
        support.ensureDatabasesDir()
        val testData = ByteArray(10 * 1024) { (it % 256).toByte() }
        val output = support.openOutput(path)
        output.write(testData, 0, testData.size)
        output.flush()
        output.close()

        // Verify size
        val size = support.getFileSize(path)
        assertNotNull(size, "File size should not be null for large file")
        assertEquals(testData.size.toLong(), size, "File size should match written data size")

        // Cleanup
        support.deleteFile(path)
    }
}
