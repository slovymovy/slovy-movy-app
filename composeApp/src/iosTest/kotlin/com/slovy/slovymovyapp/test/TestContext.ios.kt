package com.slovy.slovymovyapp.test

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.getenv

actual object TestContext {
    actual fun androidContext(): Any? {
        return null
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun getCiEnv(name: String): String? {
        return getenv(name).toString()
    }
}

actual abstract class BaseTest actual constructor()

actual typealias IgnoreIos = kotlin.test.Ignore