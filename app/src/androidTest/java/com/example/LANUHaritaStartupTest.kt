package com.example

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** Cold-start smoke test: MainActivity must launch without throwing. */
@RunWith(AndroidJUnit4::class)
class LANUHaritaStartupTest {
    @Test
    fun mainActivityLaunchesWithoutCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                check(!activity.isFinishing) { "MainActivity finished during cold start" }
            }
        }
    }
}
