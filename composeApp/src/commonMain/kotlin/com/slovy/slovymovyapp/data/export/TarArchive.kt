package com.slovy.slovymovyapp.data.export

internal object TarArchive {
    private const val blockSize = 512
    private val endOfArchive = ByteArray(blockSize * 2)

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
