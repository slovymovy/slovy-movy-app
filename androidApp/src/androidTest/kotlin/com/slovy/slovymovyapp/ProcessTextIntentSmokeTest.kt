package com.slovy.slovymovyapp

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessTextIntentSmokeTest {
    @Test
    fun launchesFromTextSelectionAction() {
        val intent = Intent(Intent.ACTION_PROCESS_TEXT)
            .setClass(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT, "gezellig")
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse("MainActivity should stay open after a PROCESS_TEXT launch.", activity.isFinishing)
                assertEquals(
                    "MainActivity should keep the delivering intent.",
                    Intent.ACTION_PROCESS_TEXT,
                    activity.intent.action
                )
            }
        }
    }
}
