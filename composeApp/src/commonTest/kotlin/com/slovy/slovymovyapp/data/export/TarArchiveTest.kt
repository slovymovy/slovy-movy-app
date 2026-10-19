package com.slovy.slovymovyapp.data.export

import com.slovy.slovymovyapp.data.remote.PlatformFileInput
import com.slovy.slovymovyapp.data.remote.PlatformFileOutput
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TarArchiveTest {

    @Test
    fun readerRoundTripsWriterOutputAcrossBlockBoundaries() {
        val entries = listOf(0, 1, 511, 512, 513, 5_000).map { size ->
            "entry-$size.db" to ByteArray(size) { (it * 7 + size).toByte() }
        }
        val archive = writeArchive(entries)

        val read = readAll(archive, chunkSize = 100)

        assertEquals(entries.map { it.first }, read.map { it.first }, "Entry names should round-trip in order")
        entries.zip(read).forEach { (expected, actual) ->
            assertContentEquals(expected.second, actual.second, "Content of ${expected.first} should round-trip")
        }
    }

    @Test
    fun readerSkipsContentTheCallbackLeavesUnread() {
        val archive = writeArchive(
            listOf(
                "first.db" to ByteArray(1_000) { 1 },
                "second.db" to ByteArray(10) { 2 },
            )
        )
        val names = mutableListOf<String>()

        TarArchive.readEntries(ByteArrayFileInput(archive, chunkSize = 64)) { name, _ -> names += name }

        assertEquals(listOf("first.db", "second.db"), names, "Unread entries must not shift the next header")
    }

    @Test
    fun paxExtendedHeadersAreSkipped() {
        val payload = ByteArray(300) { 6 }
        val archive = paxHeaderEntry("PaxHeader/only.db", "30 LIBARCHIVE.xattr.com.apple=x\n") +
            writeArchive(listOf("only.db" to payload))

        val read = readAll(archive, chunkSize = 128)

        assertEquals(listOf("only.db"), read.map { it.first }, "The pax header must not be reported as an entry")
        assertContentEquals(payload, read.single().second, "The entry after the pax header must be intact")
    }

    @Test
    fun otherEntryTypesAreRejected() {
        val archive = writeArchive(listOf("only.db" to ByteArray(10) { 7 }))
        archive[156] = '5'.code.toByte()
        rewriteChecksum(archive, headerOffset = 0)

        val failure = assertFailsWith<IllegalArgumentException> { readAll(archive, chunkSize = 256) }

        assertEquals(true, failure.message?.contains("not a regular file"), "Failure should name the type: ${failure.message}")
    }

    @Test
    fun truncatedEntryIsRejected() {
        val archive = writeArchive(listOf("only.db" to ByteArray(2_000) { 3 }))
        val truncated = archive.copyOf(512 + 700)

        val failure = assertFailsWith<IllegalArgumentException> { readAll(truncated, chunkSize = 256) }

        assertEquals(true, failure.message?.contains("truncated"), "Failure should name truncation: ${failure.message}")
    }

    @Test
    fun truncatedHeaderIsRejected() {
        val archive = writeArchive(listOf("only.db" to ByteArray(10) { 4 }))
        val truncated = archive.copyOf(300)

        assertFailsWith<IllegalArgumentException> { readAll(truncated, chunkSize = 256) }
    }

    @Test
    fun corruptedHeaderChecksumIsRejected() {
        val archive = writeArchive(listOf("only.db" to ByteArray(10) { 5 }))
        archive[5] = 'X'.code.toByte()

        assertFailsWith<IllegalArgumentException> { readAll(archive, chunkSize = 256) }
    }

    @Test
    fun emptyStreamYieldsNoEntries() {
        assertEquals(emptyList(), readAll(ByteArray(0), chunkSize = 16), "No bytes means no entries")
        assertEquals(emptyList(), readAll(ByteArray(1024), chunkSize = 16), "End-of-archive blocks mean no entries")
    }

    private fun writeArchive(entries: List<Pair<String, ByteArray>>): ByteArray {
        val output = ByteArrayFileOutput()
        writeTarArchive(
            output = output,
            entries = entries.map { (name, bytes) ->
                AppDataArchiveEntry(
                    name = name,
                    sizeBytes = bytes.size.toLong(),
                    writeContentsTo = { it.write(bytes, 0, bytes.size) },
                )
            },
            lastModifiedEpochSeconds = 1_700_000_000L,
        )
        return output.toByteArray()
    }

    /** One pax extended-header entry (type `x`) the way bsdtar writes it before the file it describes. */
    private fun paxHeaderEntry(name: String, records: String): ByteArray {
        val bytes = records.encodeToByteArray()
        val header = TarArchive.headerBytes(name, bytes.size.toLong(), lastModifiedEpochSeconds = 1_700_000_000L)
        header[156] = 'x'.code.toByte()
        rewriteChecksum(header, headerOffset = 0)
        return header + bytes + TarArchive.paddingBytes(bytes.size.toLong())
    }

    private fun rewriteChecksum(buffer: ByteArray, headerOffset: Int) {
        for (index in 148 until 156) buffer[headerOffset + index] = ' '.code.toByte()
        val checksum = (0 until 512).sumOf { buffer[headerOffset + it].toUByte().toInt() }
        checksum.toString(radix = 8).padStart(6, '0').encodeToByteArray()
            .copyInto(buffer, destinationOffset = headerOffset + 148)
        buffer[headerOffset + 154] = 0
        buffer[headerOffset + 155] = ' '.code.toByte()
    }

    private fun readAll(archive: ByteArray, chunkSize: Int): List<Pair<String, ByteArray>> {
        val read = mutableListOf<Pair<String, ByteArray>>()
        TarArchive.readEntries(ByteArrayFileInput(archive, chunkSize)) { name, content ->
            val sink = ByteArrayFileOutput()
            content.copyTo(sink)
            read += name to sink.toByteArray()
        }
        return read
    }

    /** Serves at most [chunkSize] bytes per read so partial reads are exercised. */
    private class ByteArrayFileInput(private val bytes: ByteArray, private val chunkSize: Int) : PlatformFileInput {
        private var position = 0

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= bytes.size) return -1
            val count = minOf(length, chunkSize, bytes.size - position)
            bytes.copyInto(buffer, destinationOffset = offset, startIndex = position, endIndex = position + count)
            position += count
            return count
        }

        override fun close() {}
    }

    private class ByteArrayFileOutput : PlatformFileOutput {
        private val chunks = mutableListOf<ByteArray>()

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            chunks += buffer.copyOfRange(offset, offset + length)
        }

        override fun flush() {}

        override fun close() {}

        fun toByteArray(): ByteArray {
            val result = ByteArray(chunks.sumOf { it.size })
            var position = 0
            chunks.forEach { chunk ->
                chunk.copyInto(result, destinationOffset = position)
                position += chunk.size
            }
            return result
        }
    }
}
