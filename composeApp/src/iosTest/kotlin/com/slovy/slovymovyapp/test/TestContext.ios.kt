package com.slovy.slovymovyapp.test

actual object TestContext {
    actual fun androidContext(): Any? {
        return null
    }
}

actual abstract class BaseTest actual constructor()