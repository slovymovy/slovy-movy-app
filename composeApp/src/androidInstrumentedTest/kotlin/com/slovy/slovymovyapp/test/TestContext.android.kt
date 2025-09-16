package com.slovy.slovymovyapp.test

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

actual object TestContext {
    actual fun androidContext(): Any? {
        return ApplicationProvider.getApplicationContext()
    }
}

@RunWith(AndroidJUnit4::class)
actual abstract class BaseTest actual constructor()