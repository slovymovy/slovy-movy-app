@file:OptIn(ExperimentalForeignApi::class)

package com.slovy.slovymovyapp.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH

actual fun md5(input: ByteArray): ByteArray {
    val result = ByteArray(CC_MD5_DIGEST_LENGTH)
    input.usePinned { inputPinned ->
        result.usePinned { resultPinned ->
            CC_MD5(
                inputPinned.addressOf(0),
                input.size.toUInt(),
                resultPinned.addressOf(0).reinterpret()
            )
        }
    }
    return result
}
