package com.slovy.slovymovyapp.data.export

import com.slovy.slovymovyapp.data.remote.PlatformFileInput
import com.slovy.slovymovyapp.data.remote.PlatformFileOutput
import com.slovy.slovymovyapp.data.remote.readFully

/**
 * The bytes of one tar entry while [TarArchive.readEntries] is positioned on it. Valid only inside the
 * `onEntry` callback; the reader skips whatever the callback leaves unread.
 */
internal class TarEntryContent(
    private val input: PlatformFileInput,
    val size: Long
) {
    var remaining: Long = size
        private set

    /** Copies the unread remainder of the entry to [output] and flushes it. Fails on a truncated archive. */
    fun copyTo(output: PlatformFileOutput) {
        drain { buffer, length -> output.write(buffer, 0, length) }
        output.flush()
    }

    internal fun skipRemainder() {
        drain { _, _ -> }
    }

    private fun drain(onChunk: (buffer: ByteArray, length: Int) -> Unit) {
        val buffer = ByteArray(16 * 1024)
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            require(read > 0) { "Tar entry is truncated: $remaining bytes missing." }
            onChunk(buffer, read)
            remaining -= read
        }
    }
}

internal object TarArchive {
    private const val blockSize = 512
    private const val PAX_EXTENDED_HEADER = 'x'
    private const val PAX_GLOBAL_HEADER = 'g'
    private val endOfArchive = ByteArray(blockSize * 2)

    /**
     * Walks the regular-file entries of a tar stream produced by [headerBytes] or any ustar writer,
     * calling [onEntry] with each entry's name and its content positioned at the first byte. Only
     * regular files are reported. Pax extended headers, which bsdtar adds when it re-packs a file
     * with extended attributes, are skipped; any other entry type is rejected. Does not close [input].
     */
    fun readEntries(
        input: PlatformFileInput,
        onEntry: (name: String, content: TarEntryContent) -> Unit
    ) {
        val header = ByteArray(blockSize)
        while (true) {
            val headerBytes = input.readFully(header)
            if (headerBytes == 0) return
            require(headerBytes == blockSize) { "Tar header is truncated." }
            if (header.all { it == 0.toByte() }) return

            val storedChecksum = readOctal(header, offset = 148, length = 8)
            val computedChecksum = header.withIndex().sumOf { (index, byte) ->
                if (index in 148 until 156) ' '.code else byte.toUByte().toInt()
            }
            require(storedChecksum == computedChecksum.toLong()) { "Tar header checksum mismatch." }

            val name = readString(header, offset = 0, length = 100)
            require(name.isNotEmpty()) { "Tar entry has an empty name." }
            val typeFlag = header[156].toInt().toChar()
            val size = readOctal(header, offset = 124, length = 12)

            val content = TarEntryContent(input, size)
            if (typeFlag != PAX_EXTENDED_HEADER && typeFlag != PAX_GLOBAL_HEADER) {
                require(typeFlag == '0' || typeFlag == '\u0000') { "Tar entry '$name' is not a regular file." }
                onEntry(name, content)
            }
            content.skipRemainder()

            val padding = paddingBytes(size).size
            if (padding > 0) {
                val skipped = input.readFully(ByteArray(padding))
                require(skipped == padding) { "Tar padding after '$name' is truncated." }
            }
        }
    }

    private fun readString(buffer: ByteArray, offset: Int, length: Int): String {
        var end = offset
        while (end < offset + length && buffer[end] != 0.toByte()) end++
        return buffer.decodeToString(offset, end)
    }

    private fun readOctal(buffer: ByteArray, offset: Int, length: Int): Long {
        val text = readString(buffer, offset, length).trim()
        require(text.isNotEmpty()) { "Tar header has an empty numeric field at offset $offset." }
        return text.toLongOrNull(radix = 8)
            ?: throw IllegalArgumentException("Tar header has a non-octal field at offset $offset: '$text'.")
    }

    fun headerBytes(
        name: String,
        size: Long,
        lastModifiedEpochSeconds: Long
    ): ByteArray {
        require(size >= 0) { "Tar entry size must be non-negative." }
        require(name.encodeToByteArray().size <= 100) {
            "Tar entry name '$name' is too long for the simple tar writer."
        }

        val header = ByteArray(blockSize)
        writeString(header, offset = 0, length = 100, value = name)
        writeOctal(header, offset = 100, length = 8, value = 384)
        writeOctal(header, offset = 108, length = 8, value = 0)
        writeOctal(header, offset = 116, length = 8, value = 0)
        writeOctal(header, offset = 124, length = 12, value = size)
        writeOctal(header, offset = 136, length = 12, value = lastModifiedEpochSeconds)
        for (index in 148 until 156) {
            header[index] = ' '.code.toByte()
        }
        header[156] = '0'.code.toByte()
        writeString(header, offset = 257, length = 6, value = "ustar")
        writeString(header, offset = 263, length = 2, value = "00")

        val checksum = header.sumOf { it.toUByte().toInt() }
        writeChecksum(header, checksum.toLong())
        return header
    }

    fun paddingBytes(size: Long): ByteArray {
        val remainder = (size % blockSize).toInt()
        if (remainder == 0) {
            return ByteArray(0)
        }
        return ByteArray(blockSize - remainder)
    }

    fun endOfArchiveBytes(): ByteArray = endOfArchive

    private fun writeString(
        buffer: ByteArray,
        offset: Int,
        length: Int,
        value: String
    ) {
        val bytes = value.encodeToByteArray()
        require(bytes.size <= length) {
            "Value '$value' does not fit into a $length-byte tar field."
        }
        bytes.copyInto(buffer, destinationOffset = offset)
    }

    private fun writeOctal(
        buffer: ByteArray,
        offset: Int,
        length: Int,
        value: Long
    ) {
        val digits = value.toString(radix = 8)
        require(digits.length <= length - 1) {
            "Octal value $value does not fit into a $length-byte tar field."
        }
        digits.padStart(length - 1, '0')
            .encodeToByteArray()
            .copyInto(buffer, destinationOffset = offset)
        buffer[offset + length - 1] = 0
    }

    private fun writeChecksum(
        buffer: ByteArray,
        checksum: Long
    ) {
        val digits = checksum.toString(radix = 8)
        require(digits.length <= 6) {
            "Tar checksum $checksum does not fit into the checksum field."
        }
        digits.padStart(6, '0')
            .encodeToByteArray()
            .copyInto(buffer, destinationOffset = 148)
        buffer[154] = 0
        buffer[155] = ' '.code.toByte()
    }
}
