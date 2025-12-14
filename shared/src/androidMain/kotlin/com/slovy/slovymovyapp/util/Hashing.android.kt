package com.slovy.slovymovyapp.util

import java.security.MessageDigest

actual fun md5(input: ByteArray): ByteArray {
    val digest = MessageDigest.getInstance("MD5")
    return digest.digest(input)
}
