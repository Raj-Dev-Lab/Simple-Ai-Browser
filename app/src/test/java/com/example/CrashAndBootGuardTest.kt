package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.simpleaibrowser.sab.core.browser.BootGuard
import com.simpleaibrowser.sab.core.browser.CrashGuard
import com.simpleaibrowser.sab.core.browser.SecureRouting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashAndBootGuardTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Fresh state per test
        BootGuard.bootPrefs(context).edit().clear().commit()
        SecureRouting.prefsFile(context).delete()
        CrashGuard.clear(context)
        Thread.setDefaultUncaughtExceptionHandler(null)
    }

    @Test
    fun `normal boot resets the failure counter`() {
        BootGuard.recordBootStart(context)
        BootGuard.recordBootStart(context)
        assertEquals(2, BootGuard.getBootFailures(context))
        BootGuard.markInteractive(context)
        assertEquals(0, BootGuard.getBootFailures(context))
        assertFalse(BootGuard.shouldEnterSafeMode(context))
    }

    @Test
    fun `two failed boots trigger safe mode and remove the routing prefs file`() {
        SecureRouting.applyPrefsFile(
            context,
            SecureRouting.RoutingConfig(SecureRouting.RoutingMode.TOR_ORBOT, "", 0)
        )
        assertTrue(SecureRouting.prefsFile(context).exists())

        BootGuard.recordBootStart(context)
        assertFalse(BootGuard.shouldEnterSafeMode(context))
        BootGuard.recordBootStart(context)
        assertTrue(BootGuard.shouldEnterSafeMode(context))

        assertTrue(BootGuard.enterSafeModeIfCrashLooping(context))
        // The generated startup input for GeckoView is gone; boot can proceed clean.
        assertFalse(SecureRouting.prefsFile(context).exists())
        assertEquals(0, BootGuard.getBootFailures(context))
        assertTrue(BootGuard.isSafeModeNoticePending(context))
        // Safe mode runs only once per recovery.
        assertFalse(BootGuard.enterSafeModeIfCrashLooping(context))
    }

    @Test
    fun `one failed boot does not enter safe mode`() {
        BootGuard.recordBootStart(context)
        assertFalse(BootGuard.enterSafeModeIfCrashLooping(context))
    }

    @Test
    fun `crash guard persists a readable report before delegating`() {
        val delegated = mutableListOf<Throwable>()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> delegated.add(e) }
        CrashGuard.install(context)
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        assertNotNull(handler)

        val boom = RuntimeException("startup explosion", IllegalStateException("root cause"))
        handler!!.uncaughtException(Thread.currentThread(), boom)

        // The report is persisted AND the original handler still receives the crash.
        assertEquals(1, delegated.size)
        assertEquals(boom, delegated[0])
        assertEquals(1, BootGuard.getBootFailures(context))

        val report = CrashGuard.readLastCrash(context)
        assertNotNull(report)
        assertTrue(report!!.timestampMs > 0)
        assertTrue(report.stackTrace.contains("startup explosion"))
        assertTrue(report.stackTrace.contains("root cause"))
        assertTrue(report.formatted().contains("SAB crash report"))

        CrashGuard.clear(context)
        assertNull(CrashGuard.readLastCrash(context))
    }

    @Test
    fun `prefs file validation accepts only generated proxy prefs`() {
        val valid = SecureRouting.geckoPrefsFileContent(
            SecureRouting.RoutingConfig(SecureRouting.RoutingMode.TOR_ORBOT, "", 0)
        )
        assertTrue(SecureRouting.validatePrefsFileContent(valid!!))

        assertFalse(SecureRouting.validatePrefsFileContent(""))
        assertFalse(
            SecureRouting.validatePrefsFileContent(
                "user_pref(\"network.proxy.socks\", \"1.2.3.4\");\n<corrupted>"
            )
        )
        assertFalse(
            // A tampered file must never reach GeckoRuntime
            SecureRouting.validatePrefsFileContent(
                "user_pref(\"network.proxy.socks\", \"1.2.3.4\");\n" +
                    "user_pref(\"malware.start\", true);"
            )
        )
        assertFalse(
            SecureRouting.validatePrefsFileContent(
                "user_pref(\"network.proxy.socks_port\", \"not-a-number-allowed-here\");"
            )
        )
    }
}
