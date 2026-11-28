package com.simpleaibrowser.sab.core.browser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * Incognito Auto-Shield: when a private tab is active, SAB automatically enforces the strictest
 * privacy posture regardless of the user's regular-mode settings, and benchmarks live latency
 * against trusted encrypted-DNS (DoH) providers, switching to the fastest working one.
 *
 * Pure Kotlin + java.net only: unit-testable without Android or GeckoView.
 */
object IncognitoShield {

    /** How long each DoH probe may take before the provider is marked unreachable (ms). */
    const val PROBE_TIMEOUT_MS = 2_500

    /** Each provider is probed twice; the best round-trip wins. */
    const val PROBE_ATTEMPTS = 2

    data class DohProviderSpec(
        val mode: DnsOverHttpsMode,
        val label: String,
        /** JSON-DNS probe endpoints tried in order (first successful one measures latency). */
        val probeUrls: List<String>
    )

    /**
     * Trusted security-focused resolvers. The probeUrls return RFC 8484 style JSON so the probe
     * can verify the resolver actually answers DNS (not just that TCP/TLS opened).
     * The `mode.resolverUri` values are the same URIs GeckoView consumes.
     */
    val DOH_PROVIDERS: List<DohProviderSpec> = listOf(
        DohProviderSpec(
            mode = DnsOverHttpsMode.QUAD9,
            label = "Quad9 Security",
            probeUrls = listOf(
                "https://dns.quad9.net:5053/dns-query?name=example.com&type=A",
                "https://dns.quad9.net/dns-query?name=example.com&type=A"
            )
        ),
        DohProviderSpec(
            mode = DnsOverHttpsMode.ADGUARD,
            label = "AdGuard DoH",
            probeUrls = listOf(
                "https://dns.adguard-dns.gov/resolve?name=example.com",
                "https://dns.adguard-dns.com/dns-query?name=example.com&type=A"
            )
        ),
        DohProviderSpec(
            mode = DnsOverHttpsMode.CLOUDFLARE,
            label = "Cloudflare 1.1.1.1",
            probeUrls = listOf(
                "https://mozilla.cloudflare-dns.com/dns-query?name=example.com&type=A"
            )
        )
    )

    data class BenchmarkWinner(
        val mode: DnsOverHttpsMode,
        val label: String,
        val bestLatencyMs: Int
    )

    sealed interface ShieldPhase {
        data object Idle : ShieldPhase
        data object Benchmarking : ShieldPhase
        data class Active(val winner: BenchmarkWinner?) : ShieldPhase
    }

    /**
     * The privacy config enforced for private tabs: strictest tracking protection, fingerprinting
     * defense, total cookie isolation, tracking-parameter stripping, bounce-tracking protection,
     * local-network protection, safe browsing and HTTPS-only — all forced ON regardless of the
     * user's regular settings. DoH comes from the benchmark winner when available, otherwise the
     * user's own DoH setting is preserved (never silently disabled).
     */
    fun shieldedConfig(
        user: PrivacyConfig,
        winner: BenchmarkWinner? = null
    ): PrivacyConfig {
        val dohMode = when {
            winner != null -> winner.mode
            user.dnsOverHttpsMode != DnsOverHttpsMode.OFF -> user.dnsOverHttpsMode
            else -> DnsOverHttpsMode.OFF
        }
        return user.copy(
            trackingProtectionLevel = TrackingProtectionLevel.STRICT,
            doNotTrackEnabled = true,
            globalPrivacyControlEnabled = true,
            cookieIsolationMode = CookieIsolationMode.TOTAL_ISOLATE,
            cookiePurgingEnabled = true,
            stripTrackingParams = true,
            bounceTrackingProtection = true,
            fingerprintingDefense = true,
            localNetworkProtection = true,
            safeBrowsingEnabled = true,
            httpsOnlyEnabled = true,
            dnsOverHttpsMode = dohMode,
            dohStrict = if (winner != null) true else user.dohStrict
        )
    }

    /** Validates that a DoH JSON response actually resolved DNS (Status == 0 and answers present). */
    fun isDohJsonAnswer(body: String): Boolean {
        if (body.isBlank()) return false
        val statusMatch = Regex("\"Status\"\\s*:\\s*(\\d+)").find(body) ?: return false
        val status = statusMatch.groupValues[1].toIntOrNull() ?: return false
        if (status != 0) return false
        return body.contains("\"Answer\"") || body.contains("\"data\"")
    }

    /**
     * Probes every provider twice (best round-trip wins) using java.net URL connections.
     * A provider only counts when the response is a valid DNS JSON answer — a TLS handshake
     * alone does not prove the resolver works.
     */
    suspend fun benchmark(
        timeoutMs: Int = PROBE_TIMEOUT_MS,
        attempts: Int = PROBE_ATTEMPTS,
        connectionFactory: (String) -> HttpURLConnection = { url ->
            (URL(url).openConnection() as HttpURLConnection)
        }
    ): BenchmarkWinner? = coroutineScope {
        val jobs = DOH_PROVIDERS.map { spec ->
            async(Dispatchers.IO) { measure(spec, timeoutMs, attempts, connectionFactory) }
        }
        val winners = jobs.mapNotNull { it.await() }
        winners.minByOrNull { it.bestLatencyMs }
    }

    private suspend fun measure(
        spec: DohProviderSpec,
        timeoutMs: Int,
        attempts: Int,
        connectionFactory: (String) -> HttpURLConnection
    ): BenchmarkWinner? = withContext(Dispatchers.IO) {
        var best: Int? = null
        for (attempt in 0 until attempts) {
            for (probeUrl in spec.probeUrls) {
                val latency = withTimeoutOrNull(timeoutMs.toLong()) {
                    probeOnce(probeUrl, timeoutMs, connectionFactory)
                } ?: continue
                if (latency != null && (best == null || latency < best!!)) {
                    best = latency
                }
                if (best != null) break
            }
            if (best != null) break
        }
        best?.let { BenchmarkWinner(spec.mode, spec.label, it) }
    }

    /** Returns round-trip milliseconds for a valid DNS answer, or null when invalid/unreachable. */
    private fun probeOnce(
        probeUrl: String,
        timeoutMs: Int,
        connectionFactory: (String) -> HttpURLConnection
    ): Int? {
        val started = System.currentTimeMillis()
        var connection: HttpURLConnection? = null
        return try {
            connection = connectionFactory(probeUrl).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/dns-json")
                setRequestProperty("User-Agent", "SAB-IncognitoShield/1.0")
            }
            val code = connection.responseCode
            if (code !in 200..299) return null
            val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            if (!isDohJsonAnswer(body)) return null
            val elapsed = (System.currentTimeMillis() - started).toInt()
            elapsed
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }
}
