package com.slovy.slovymovyapp.test

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

actual object TestContext {
    actual fun androidContext(): Any? {
        return null
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun getCiEnv(name: String): String? {
        val env = getenv(name)?.toKString()
        return if (env.isNullOrEmpty()) null else env
    }
}

actual abstract class BaseTest actual constructor()

actual typealias IgnoreIos = kotlin.test.Ignore
