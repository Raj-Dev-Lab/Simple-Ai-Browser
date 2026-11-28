package com.simpleaibrowser.sab.core.browser

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.system.exitProcess

/**
 * Secure Routing: route SAB's network traffic through a user-chosen SOCKS5 endpoint —
 * Tor/Orbot (127.0.0.1:9050), the user's own trusted SOCKS5 server, or an explicitly
 * experimental Public Proxy Relay candidate.
 *
 * Two real transport layers:
 *  1. GeckoView page traffic — Gecko honors `network.proxy.*` preferences supplied through a
 *     user.js-style config file (`GeckoRuntimeSettings.Builder.configFilePath`, verified in the
 *     GeckoView 161 artifact). The file is written on every routing change; GeckoRuntime is a
 *     process-lifetime singleton, so page-traffic routing takes effect at next app launch
 *     (tabs are restored automatically by session hydration). The UI says this plainly.
 *  2. App-level OkHttp traffic (downloads, media fetch, extension updates) — a SOCKS5
 *     java.net.Proxy applied to the shared clients, effective immediately.
 *
 * DNS goes through the tunnel (`network.proxy.socks_remote_dns=true`) so Tor never leaks DNS.
 */
object SecureRouting {

    const val PREFS_FILE_NAME = "sab_gecko_user_prefs.js"
    const val DEFAULT_TOR_HOST = "127.0.0.1"
    const val DEFAULT_TOR_PORT = 9050
    const val ORBOT_PACKAGE = "org.torproject.android"

    enum class RoutingMode(val id: String, val label: String, val subtitle: String) {
        OFF("off", "No Routing", "Direct connection using your normal network"),
        TOR_ORBOT(
            "tor_orbot",
            "Tor / Orbot (SOCKS5)",
            "Route through Orbot at 127.0.0.1:9050 — genuinely private, slower"
        ),
        SOCKS5(
            "socks5",
            "Custom Trusted SOCKS5",
            "Route through your own SOCKS5 server (self-hosted or trusted)"
        ),
        PUBLIC_RELAY(
            "public_relay",
            "Public Proxy Relay (Experimental)",
            "Unverified third-party servers — use at your own risk, NOT a VPN"
        );

        companion object {
            fun fromId(id: String): RoutingMode = entries.firstOrNull { it.id == id } ?: OFF
        }
    }

    data class RoutingConfig(
        val mode: RoutingMode,
        val socksHost: String,
        val socksPort: Int
    ) {
        val isActive: Boolean get() = mode != RoutingMode.OFF
    }

    fun isOrbotInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(ORBOT_PACKAGE, 0)
        true
    }.getOrDefault(false)

    /** Normalized endpoint for a routing config (Tor defaults to Orbot's local port). */
    fun endpointFor(config: RoutingConfig): Pair<String, Int> = when (config.mode) {
        RoutingMode.OFF -> "" to 0
        RoutingMode.TOR_ORBOT -> DEFAULT_TOR_HOST to DEFAULT_TOR_PORT
        else -> config.socksHost.trim() to config.socksPort
    }

    /**
     * user.js-format preferences that force all Gecko traffic (including DNS) through SOCKS5.
     * Returns null for OFF (the file should then be deleted).
     */
    fun geckoPrefsFileContent(config: RoutingConfig): String? {
        val (host, port) = endpointFor(config)
        if (config.mode == RoutingMode.OFF || host.isBlank() || port !in 1..65535) return null
        return buildString {
            appendLine("// SAB Secure Routing — generated file, do not edit")
            appendLine("user_pref(\"network.proxy.type\", 1);")
            appendLine("user_pref(\"network.proxy.socks\", \"$host\");")
            appendLine("user_pref(\"network.proxy.socks_port\", $port);")
            appendLine("user_pref(\"network.proxy.socks_version\", 5);")
            appendLine("user_pref(\"network.proxy.socks_remote_dns\", true);")
            appendLine("user_pref(\"network.proxy.no_proxies_on\", \"\");")
        }
    }

    fun prefsFile(context: Context): File = File(context.filesDir, PREFS_FILE_NAME)

    /** Writes (or clears) the Gecko config file according to the routing config. */
    fun applyPrefsFile(context: Context, config: RoutingConfig): Boolean {
        return runCatching {
            val file = prefsFile(context)
            val content = geckoPrefsFileContent(config)
            if (content == null) {
                if (file.exists()) file.delete()
            } else {
                file.parentFile?.mkdirs()
                file.writeText(content)
            }
            true
        }.getOrDefault(false)
    }

    /** SOCKS5 proxy for the app's OkHttp clients; null when routing is off. */
    fun okHttpProxy(config: RoutingConfig, overrideHost: String? = null, overridePort: Int? = null): Proxy? {
        val host = overrideHost ?: endpointFor(config).first
        val port = overridePort ?: endpointFor(config).second
        if (host.isBlank() || port !in 1..65535) return null
        return runCatching {
            Proxy(Proxy.Type.SOCKS, InetSocketAddress(host, port))
        }.getOrNull()
    }

    /**
     * Restarts the app process so the Gecko config file takes effect (standard alarm-based
     * relaunch pattern; tabs are restored by session hydration on the fresh launch).
     */
    fun restartApp(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launchIntent == null) {
            exitProcess(0)
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pendingIntent = PendingIntent.getActivity(
            context,
            70707,
            launchIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarm.canScheduleExactAlarms()) {
            alarm.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 350, pendingIntent)
        } else {
            alarm.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 350,
                pendingIntent
            )
        }
        Handler(Looper.getMainLooper()).postDelayed({ exitProcess(0) }, 150)
    }
}
