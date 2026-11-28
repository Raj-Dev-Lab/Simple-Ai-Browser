package com.example

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.simpleaibrowser.sab.core.browser.BootGuard
import com.simpleaibrowser.sab.core.browser.CrashGuard
import com.simpleaibrowser.sab.core.browser.SecureRouting

/**
 * Recovery launcher: a second home-screen icon that NEVER touches the browser engine, the
 * ViewModel or Compose. It exists for the situation where the main app crashes on every open
 * and no logcat is available — the crash report can be viewed and shared from here, and safe
 * mode (forcing the WebView fallback engine) can be started with one tap.
 *
 * Implemented with plain programmatic Views (no XML, no theme-dependent inflation) so its own
 * startup surface is as small as possible. Every action is individually wrapped in runCatching.
 */
class SabRecoveryActivity : Activity() {

    private lateinit var reportView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = dp(20)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            setBackgroundColor(Color.BLACK)
        }

        root.addView(TextView(this).apply {
            text = "SAB Recovery"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })

        root.addView(TextView(this).apply {
            text = "If the browser does not open normally, use the buttons below. " +
                "You can share the recorded crash report with the developer."
            textSize = 14f
            setTextColor(0xFFB0B0B0.toInt())
            setPadding(0, dp(8), 0, dp(16))
        })

        reportView = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFFE0E0E0.toInt())
            setPadding(0, dp(12), 0, dp(12))
        }

        root.addView(
            ScrollView(this).apply {
                addView(reportView)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
            }
        )

        root.addView(button("Share crash report") { shareReport() })
        root.addView(
            button("Start in safe mode (fallback engine)") {
                BootGuard.setEngineLockout(this, true)
                BootGuard.bootPrefs(this).edit()
                    .putInt("boot_failures", 0)
                    .putBoolean("safe_mode_notice_pending", true)
                    .apply()
                SecureRouting.prefsFile(this).delete()
                CrashGuard.clear(this)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        )
        root.addView(button("Clear diagnostics") {
            CrashGuard.clear(this)
            refreshReport()
        })
        root.addView(
            button("Open SAB normally", primary = false) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        )

        setContentView(root)
        refreshReport()
    }

    private fun refreshReport() {
        reportView.text = buildString {
            appendLine("Status")
            appendLine("  Failed boot attempts: ${BootGuard.getBootFailures(this@SabRecoveryActivity)}")
            appendLine("  Fallback engine lockout: ${if (BootGuard.isEngineLockout(this@SabRecoveryActivity)) "ACTIVE" else "off"}")
            appendLine("  Secure Routing file: ${if (SecureRouting.prefsFile(this@SabRecoveryActivity).exists()) "present" else "not present"}")
            appendLine()
            val crash = runCatching { CrashGuard.readLastCrash(this@SabRecoveryActivity) }.getOrNull()
            if (crash == null) {
                append("No crash has been recorded.")
            } else {
                append(crash.formatted())
            }
        }
    }

    private fun shareReport() {
        runCatching {
            val report = runCatching { CrashGuard.readLastCrash(this)?.formatted() }.getOrNull()
            val text = report ?: "SAB crash report\n(no crash recorded on this device)"
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "SAB crash report")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(send, "Share SAB crash report"))
        }
    }

    private fun button(label: String, primary: Boolean = true, onClick: () -> Unit): View =
        Button(this).apply {
            text = label
            setOnClickListener { runCatching { onClick() } }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6); gravity = Gravity.CENTER }
            if (primary) {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            }
        }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()
}
