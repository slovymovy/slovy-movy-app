package com.slovy.slovymovyapp.util

/**
 * Computes MD5 hash of the input byte array.
 *
 * This function is deterministic and produces identical output across all platforms
 * (Android, iOS, Desktop/JVM) per RFC 1321.
 *
 * @param input The byte array to hash
 * @return 16-byte MD5 hash
 */
expect fun md5(input: ByteArray): ByteArray
