package com.slovy.slovymovyapp.test

import com.slovy.slovymovyapp.data.remote.PlatformDbSupport

expect object TestContext {
    fun androidContext(): Any?
}

expect abstract class BaseTest()

fun platformDbSupport(): PlatformDbSupport = PlatformDbSupport(TestContext.androidContext())
