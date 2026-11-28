package com.simpleaibrowser.sab.core.browser

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BootGuard + CrashGuard: turns "app crashes when opened" from an invisible failure into a
 * diagnosable, self-healing state.
 *
 * BootGuard tracks consecutive boots that never reached an interactive state. Two or more in a
 * row means a startup crash loop (typically a native GeckoView crash that no try/catch can
 * intercept). The engine factory consults BootGuard before creating the runtime and enters
 * SAFE MODE: the Secure Routing prefs file — the only startup-time input SAB generates for
 * GeckoView — is removed so Gecko can boot clean; the user sees a notice in Settings →
 * Diagnostics and can re-enable routing afterwards.
 *
 * CrashGuard wraps the default UncaughtExceptionHandler: it persists the full stack trace of
 * any JVM crash to a file BEFORE delegating to the system handler (the system crash dialog is
 * preserved), bumping the boot-failure counter as well.
 */
object BootGuard {

    private const val PREFS_NAME = "sab_boot_guard"
    private const val KEY_BOOT_FAILURES = "boot_failures"
    private const val KEY_SAFE_MODE_NOTICE = "safe_mode_notice_pending"
    private const val KEY_SAFE_MODE_TRIED = "safe_mode_tried"
    private const val KEY_ENGINE_LOCKOUT = "engine_lockout"
    private const val SAFE_MODE_THRESHOLD = 2

    fun bootPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordBootStart(context: Context) {
        bootPrefs(context).edit().apply {
            putInt(KEY_BOOT_FAILURES, getBootFailures(context) + 1)
            apply()
        }
    }

    /**
     * Called once the app actually reached an interactive UI — a successful boot. Resets the
     * failure counter and the safe-mode-tried latch, but NOT the engine lockout: after two
     * full crash loops the fallback engine stays in place until the user explicitly re-enables
     * GeckoView in Settings → Diagnostics.
     */
    fun markInteractive(context: Context) {
        bootPrefs(context).edit()
            .putInt(KEY_BOOT_FAILURES, 0)
            .putBoolean(KEY_SAFE_MODE_NOTICE, false)
            .putBoolean(KEY_SAFE_MODE_TRIED, false)
            .apply()
    }

    fun getBootFailures(context: Context): Int =
        bootPrefs(context).getInt(KEY_BOOT_FAILURES, 0)

    fun isSafeModeNoticePending(context: Context): Boolean =
        bootPrefs(context).getBoolean(KEY_SAFE_MODE_NOTICE, false)

    /** Sticky lockout: force the CompatWebViewEngine after repeated startup crash loops. */
    fun isEngineLockout(context: Context): Boolean =
        bootPrefs(context).getBoolean(KEY_ENGINE_LOCKOUT, false)

    fun setEngineLockout(context: Context, enabled: Boolean) {
        bootPrefs(context).edit().putBoolean(KEY_ENGINE_LOCKOUT, enabled).apply()
    }

    /** Crash loops (>= threshold failed boots) trigger safe mode and clear the notice flag. */
    fun shouldEnterSafeMode(context: Context): Boolean =
        getBootFailures(context) >= SAFE_MODE_THRESHOLD

    /**
     * Engine-creation hook with a two-stage escalation ladder:
     *
     *  1. First crash loop (>= 2 failed boots, safe mode not tried yet): SAFE MODE — the
     *     routing prefs file (the one generated startup input for Gecko) is deleted so
     *     GeckoView boots clean.
     *  2. Second crash loop (>= 2 more failed boots with safe mode already tried): ENGINE
     *     LOCKOUT — the factory stops creating GeckoView entirely and uses the WebView
     *     fallback, which survives even a broken Gecko native setup. The lockout is sticky;
     *     Settings → Diagnostics offers an explicit re-enable.
     *
     * Returns true when a recovery action was taken on this boot.
     */
    fun enterSafeModeIfCrashLooping(context: Context): Boolean {
        if (!shouldEnterSafeMode(context)) return false
        val triedBefore = bootPrefs(context).getBoolean(KEY_SAFE_MODE_TRIED, false)
        val removed = SecureRouting.prefsFile(context).delete()
        bootPrefs(context).edit().apply {
            putInt(KEY_BOOT_FAILURES, 0)
            putBoolean(KEY_SAFE_MODE_NOTICE, true)
            putBoolean(KEY_SAFE_MODE_TRIED, true)
            if (triedBefore) putBoolean(KEY_ENGINE_LOCKOUT, true)
            apply()
        }
        Log.w(
            "SabBootGuard",
            if (triedBefore) {
                "Crash loop CONTINUES after safe mode — engine lockout active " +
                    "(routing prefs file removed=$removed)"
            } else {
                "Crash loop detected (>= $SAFE_MODE_THRESHOLD failed boots). " +
                    "Safe mode: routing prefs file removed=$removed"
            }
        )
        return true
    }
}

object CrashGuard {

    private const val CRASH_DIR = "crash_guard"
    private const val CRASH_FILE = "last_crash.txt"
    private const val MAX_TRACE_CHARS = 24_000

    /** Immutable diagnostics snapshot consumed by Settings → DIAGNOSTICS. */
    data class DiagnosticsSnapshot(
        val engineSummary: String,
        val bootFailures: Int,
        val safeModeNotice: Boolean,
        val engineLockout: Boolean,
        val routingPrefsFilePresent: Boolean,
        val routingModeLabel: String,
        val lastCrash: CrashReport?
    )

    data class CrashReport(
        val timestampMs: Long,
        val threadName: String,
        val stackTrace: String
    ) {
        fun formatted(): String {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestampMs))
            return "SAB crash report\nTime: $time\nThread: $threadName\n\n$stackTrace"
        }
    }

    /** Installs the JVM crash interceptor. Call once from Application.onCreate. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { persistCrash(appContext, thread, throwable) }
            BootGuard.recordBootStart(appContext)
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun persistCrash(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, CRASH_DIR)
        if (!dir.exists()) dir.mkdirs()
        val trace = throwable.stackTraceToString().let {
            if (it.length > MAX_TRACE_CHARS) it.take(MAX_TRACE_CHARS) + "\n…truncated" else it
        }
        val cause = throwable.cause
        val body = buildString {
            appendLine(throwable.javaClass.name)
            appendLine(if (throwable.message.isNullOrBlank()) "(no message)" else throwable.message)
            if (cause != null) {
                appendLine("Caused by: ${cause.javaClass.name}: ${cause.message}")
            }
            appendLine(trace)
        }
        File(dir, CRASH_FILE).writeText(
            CrashReport(System.currentTimeMillis(), thread.name, body).formatted()
        )
    }

    fun readLastCrash(context: Context): CrashReport? {
        val file = File(File(context.filesDir, CRASH_DIR), CRASH_FILE)
        if (!file.exists()) return null
        return runCatching {
            val text = file.readText()
            // formatted(): "SAB crash report\nTime: <ts>\nThread: <name>\n\n<stack>"
            val lines = text.lineSequence().toList()
            val time = lines.firstOrNull { it.startsWith("Time: ") }
                ?.removePrefix("Time: ") ?: ""
            val parsed = runCatching {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(time)?.time
            }.getOrNull() ?: 0L
            val thread = lines.firstOrNull { it.startsWith("Thread: ") }
                ?.removePrefix("Thread: ") ?: "unknown"
            val stack = text.substringAfter("\n\n", missingDelimiterValue = text)
            CrashReport(parsed, thread, stack)
        }.getOrNull()
    }

    fun clear(context: Context) {
        runCatching {
            File(File(context.filesDir, CRASH_DIR), CRASH_FILE).delete()
        }
    }
}
