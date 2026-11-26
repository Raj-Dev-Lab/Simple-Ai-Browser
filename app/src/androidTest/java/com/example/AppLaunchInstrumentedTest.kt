package com.example

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Boots the real app on a real Android system (an emulator in CI) and proves the browser shell
 * starts and survives its start-up: splash reveal, GeckoView runtime creation and session restore.
 * If anything crashes on the main thread the process dies and this test is reported as failed.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchInstrumentedTest {

    @Test
    fun activityStartsAndStaysAlive() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Give the splash reveal and the GeckoView start-up time to finish.
            Thread.sleep(15_000)

            scenario.onActivity { activity ->
                assertFalse("MainActivity finished during start-up", activity.isFinishing)
            }
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
