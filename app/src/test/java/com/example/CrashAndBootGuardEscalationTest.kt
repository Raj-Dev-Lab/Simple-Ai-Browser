package com.example

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.simpleaibrowser.sab.core.browser.BootGuard
import com.simpleaibrowser.sab.core.browser.CrashGuard
import com.simpleaibrowser.sab.core.browser.SecureRouting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Two-stage escalation ladder: first crash loop -> safe mode (delete routing prefs file);
 * second crash loop -> sticky engine lockout (factory falls back to WebView engine).
 * Also covers the recovery launcher (SabRecoveryActivity) end-to-end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashAndBootGuardEscalationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        BootGuard.bootPrefs(context).edit().clear().commit()
        SecureRouting.prefsFile(context).delete()
        CrashGuard.clear(context)
        Thread.setDefaultUncaughtExceptionHandler(null)
    }

    private fun failBootTwice() {
        BootGuard.recordBootStart(context)
        BootGuard.recordBootStart(context)
    }

    private fun allTexts(root: View): String {
        val sb = StringBuilder()
        fun collect(v: View) {
            if (v is TextView) sb.append(v.text).append('\n')
            if (v is ViewGroup) for (i in 0 until v.childCount) collect(v.getChildAt(i))
        }
        collect(root)
        return sb.toString()
    }

    @Test
    fun `first crash loop enters safe mode only`() {
        SecureRouting.applyPrefsFile(
            context,
            SecureRouting.RoutingConfig(SecureRouting.RoutingMode.TOR_ORBOT, "", 0)
        )
        assertTrue(SecureRouting.prefsFile(context).exists())

        failBootTwice()
        assertTrue(BootGuard.enterSafeModeIfCrashLooping(context))
        assertFalse(SecureRouting.prefsFile(context).exists())
        assertFalse("lockout must NOT trigger on the first loop", BootGuard.isEngineLockout(context))

        // A successful interactive boot resets the safe-mode latch but not a lockout.
        BootGuard.markInteractive(context)
        assertFalse(BootGuard.isSafeModeNoticePending(context))
    }

    @Test
    fun `second crash loop escalates to a sticky engine lockout`() {
        failBootTwice()
        assertTrue(BootGuard.enterSafeModeIfCrashLooping(context))
        assertFalse(BootGuard.isEngineLockout(context))

        // The user keeps crashing even in safe mode: two more failed boots.
        failBootTwice()
        assertTrue(BootGuard.enterSafeModeIfCrashLooping(context))
        assertTrue(BootGuard.isEngineLockout(context))

        // A successful boot does NOT clear the lockout — it is re-enabled explicitly.
        BootGuard.markInteractive(context)
        assertTrue(BootGuard.isEngineLockout(context))

        // Explicit re-enable (Settings → Diagnostics) clears it.
        BootGuard.setEngineLockout(context, false)
        assertFalse(BootGuard.isEngineLockout(context))
    }

    @Test
    fun `recovery activity shows status, records crash, and safe-mode button locks out the engine`() {
        // 1. Fresh recovery screen: no crash recorded yet.
        ActivityScenario.launch(SabRecoveryActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val content = allTexts(activity.window.decorView)
                assertTrue(content.contains("SAB Recovery"))
                assertTrue(content.contains("No crash has been recorded"))
            }
        }

        // 2. A crash happens (the handler the recovery path relies on):
        CrashGuard.install(context)
        Thread.getDefaultUncaughtExceptionHandler()!!
            .uncaughtException(Thread.currentThread(), RuntimeException("boot exploded"))
        assertEquals(1, BootGuard.getBootFailures(context))

        // 3. Reopen recovery: the report text must now be visible in the UI.
        ActivityScenario.launch(SabRecoveryActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(allTexts(activity.window.decorView).contains("boot exploded"))
            }
        }

        // 4. Tap "Start in safe mode": engine lockout ON, routing prefs gone, counter reset.
        ActivityScenario.launch(SabRecoveryActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var clicked = false
                fun clickSafeMode(v: View) {
                    if (v is Button && v.text.contains("safe mode")) {
                        v.performClick()
                        clicked = true
                    }
                    if (v is ViewGroup) for (i in 0 until v.childCount) clickSafeMode(v.getChildAt(i))
                }
                clickSafeMode(activity.window.decorView)
                assertTrue("safe mode button must exist", clicked)
            }
        }
        assertTrue(BootGuard.isEngineLockout(context))
        assertFalse(SecureRouting.prefsFile(context).exists())
        assertEquals(0, BootGuard.getBootFailures(context))
        // Safe mode intentionally clears diagnostics as part of recovery — the crash report
        // was already verified visible on screen in step 3 above.
    }
}
