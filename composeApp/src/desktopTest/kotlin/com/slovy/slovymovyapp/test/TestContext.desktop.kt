package com.slovy.slovymovyapp.test

actual object TestContext {
    actual fun androidContext(): Any? {
        return null
    }

    actual fun getCiEnv(name: String): String? {
        return System.getenv(name)
    }
}

actual abstract class BaseTest actual constructor()

@Target(allowedTargets = [AnnotationTarget.CLASS, AnnotationTarget.FUNCTION])
actual annotation class IgnoreIos actual constructor()