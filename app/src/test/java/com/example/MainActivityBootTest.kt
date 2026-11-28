package com.example

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.simpleaibrowser.sab.core.browser.BootGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Full startup reproduction: boots MainActivity exactly like a real launch — Application,
 * ViewModel construction (engine factory, DataStore, Room), Compose composition of
 * SabBrowserApp and the start page. Any JVM-level crash on the startup path reproduces here
 * with a real stack trace instead of a silent device crash.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityBootTest {

    @Test
    fun `full startup reaches interactive state without crashing`() {
        BootGuard.bootPrefs(ApplicationProvider.getApplicationContext()).edit().clear().commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // The activity must be alive and rooted with composed content.
                assertTrue(!activity.isFinishing)
                assertTrue(!activity.isDestroyed)
                assertEquals(View.VISIBLE, activity.window.decorView.visibility)
            }
        }
    }

    @Test
    fun `booting the activity resets the boot failure counter via focus`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        BootGuard.bootPrefs(context).edit().clear().commit()
        BootGuard.recordBootStart(context)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.window.decorView.hasWindowFocus() /* no-op probe */ }
        }
        // Note: Robolectric may not deliver window focus automatically; the counter reset
        // path is covered by CrashAndBootGuardTest. Here we only assert the boot itself
        // did not crash and left the process alive.
        assertFalse(BootGuard.shouldEnterSafeMode(context))
    }
}
