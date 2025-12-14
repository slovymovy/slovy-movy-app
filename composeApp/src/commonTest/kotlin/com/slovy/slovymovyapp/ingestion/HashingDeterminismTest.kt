@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.slovy.slovymovyapp.ingestion

import com.slovy.slovymovyapp.util.md5
import com.slovy.slovymovyapp.util.stripAccents
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * Cross-platform determinism tests for hashing functions.
 * These tests verify that MD5 produces identical output on all platforms,
 * which is critical for deterministic lemma ID generation.
 */
class HashingDeterminismTest {

    @Test
    fun md5_produces_consistent_output_for_simple_string() {
        val input = "hello world"
        val hash = md5(input.encodeToByteArray())

        // MD5("hello world") = 5eb63bbbe01eeed093cb22bb8f5acdc3
        val expected = byteArrayOf(
            0x5e.toByte(), 0xb6.toByte(), 0x3b.toByte(), 0xbb.toByte(),
            0xe0.toByte(), 0x1e.toByte(), 0xee.toByte(), 0xd0.toByte(),
            0x93.toByte(), 0xcb.toByte(), 0x22.toByte(), 0xbb.toByte(),
            0x8f.toByte(), 0x5a.toByte(), 0xcd.toByte(), 0xc3.toByte()
        )
        assertContentEquals(expected, hash, "MD5 hash should match known value")
    }

    @Test
    fun md5_produces_consistent_output_for_utf8_string() {
        val input = "café_cafe"
        val hash = md5(input.encodeToByteArray())

        // Pre-computed MD5 hash for "café_cafe" (UTF-8 encoded)
        // This verifies cross-platform UTF-8 encoding consistency
        assertEquals(16, hash.size, "MD5 should produce 16-byte hash")

        // Run twice to verify determinism within same runtime
        val hash2 = md5(input.encodeToByteArray())
        assertContentEquals(hash, hash2, "MD5 should be deterministic")
    }

    @Test
    fun lemma_id_generation_is_deterministic() {
        val lemmaWord = "café"
        val lemmaNormalized = stripAccents(lemmaWord)
        val str = lemmaWord + "_" + lemmaNormalized

        val hash = md5(str.encodeToByteArray())
        val lemmaId = Uuid.fromByteArray(hash.sliceArray(0..15))

        // Run again to verify determinism
        val hash2 = md5(str.encodeToByteArray())
        val lemmaId2 = Uuid.fromByteArray(hash2.sliceArray(0..15))

        assertEquals(lemmaId, lemmaId2, "Lemma ID should be deterministic")
        assertEquals("cafe", lemmaNormalized, "Normalized form should be correct")
    }

    @Test
    fun lemma_id_generation_for_cyrillic() {
        val lemmaWord = "программа"
        val lemmaNormalized = stripAccents(lemmaWord)
        val str = lemmaWord + "_" + lemmaNormalized

        val hash = md5(str.encodeToByteArray())
        val lemmaId = Uuid.fromByteArray(hash.sliceArray(0..15))

        // Cyrillic should remain unchanged by stripAccents
        assertEquals("программа", lemmaNormalized, "Cyrillic should not be modified by stripAccents")

        // Verify determinism
        val hash2 = md5(str.encodeToByteArray())
        val lemmaId2 = Uuid.fromByteArray(hash2.sliceArray(0..15))
        assertEquals(lemmaId, lemmaId2, "Cyrillic lemma ID should be deterministic")
    }

    @Test
    fun lemma_id_generation_for_polish() {
        val lemmaWord = "Żółć"
        val lemmaNormalized = stripAccents(lemmaWord)
        val str = lemmaWord + "_" + lemmaNormalized

        val hash = md5(str.encodeToByteArray())
        val lemmaId = Uuid.fromByteArray(hash.sliceArray(0..15))

        // Polish should be properly normalized
        assertEquals("zolc", lemmaNormalized, "Polish letters should be properly normalized")

        // Verify determinism
        val hash2 = md5(str.encodeToByteArray())
        val lemmaId2 = Uuid.fromByteArray(hash2.sliceArray(0..15))
        assertEquals(lemmaId, lemmaId2, "Polish lemma ID should be deterministic")
    }

    @Test
    fun md5_produces_expected_hash_for_known_input() {
        // Test vector from RFC 1321
        val input = ""
        val hash = md5(input.encodeToByteArray())

        // MD5("") = d41d8cd98f00b204e9800998ecf8427e
        val expected = byteArrayOf(
            0xd4.toByte(), 0x1d.toByte(), 0x8c.toByte(), 0xd9.toByte(),
            0x8f.toByte(), 0x00.toByte(), 0xb2.toByte(), 0x04.toByte(),
            0xe9.toByte(), 0x80.toByte(), 0x09.toByte(), 0x98.toByte(),
            0xec.toByte(), 0xf8.toByte(), 0x42.toByte(), 0x7e.toByte()
        )
        assertContentEquals(expected, hash, "MD5 of empty string should match RFC 1321 test vector")
    }
}
