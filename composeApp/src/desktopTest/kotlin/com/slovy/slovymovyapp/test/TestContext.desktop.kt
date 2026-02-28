package com.slovy.slovymovyapp.test

actual object TestContext {
    actual fun androidContext(): Any? {
        return null
    }

    actual fun getCiEnv(name: String): String? {
        val env = System.getenv(name)
        return if (env.isNullOrEmpty()) null else env
    }

    actual fun testServerHost(): String = "127.0.0.1"
}

actual abstract class BaseTest actual constructor() : BaseTestImpl()

@Target(allowedTargets = [AnnotationTarget.CLASS, AnnotationTarget.FUNCTION])
actual annotation class IgnoreIos actual constructor()

@Target(allowedTargets = [AnnotationTarget.CLASS, AnnotationTarget.FUNCTION])
actual annotation class IgnoreRobolectric actual constructor()

actual fun testAssume(condition: Boolean, message: String) {
    org.junit.Assume.assumeTrue(message, condition)
}
