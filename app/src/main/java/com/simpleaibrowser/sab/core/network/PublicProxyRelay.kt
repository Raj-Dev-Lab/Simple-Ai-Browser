package com.simpleaibrowser.sab.core.network

import java.io.BufferedInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

/**
 * Public Proxy Relay (EXPERIMENTAL — explicitly labeled "Unverified Third-Party Servers").
 *
 * Fetches public SOCKS5 proxy lists, probes every candidate through a REAL SOCKS5 connection
 * (java.net.Socket with a SOCKS Proxy — JDK-native, no extra dependency), measures latency and
 * captures the exit IP so the user sees which server they would actually appear as. Routing then
 * happens through the same SOCKS5 mechanism as Tor/Orbot.
 *
 * SECURITY: these are unverified public servers. The UI must always show the experimental
 * warning; traffic through them can be observed by the proxy operator. This is not a VPN.
 */
object PublicProxyRelay {

    const val PROBE_TIMEOUT_MS = 4_000
    const val MAX_CANDIDATES = 250
    const val TOP_RESULTS = 10

    /** Real, maintained public SOCKS5 feeds (plain "ip:port" lines). */
    val FEED_URLS: List<String> = listOf(
        "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/socks5.txt",
        "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/socks5.txt",
        "https://raw.githubusercontent.com/hookzof/socks5_list/master/proxy.txt"
    )

    /** Exit-IP check endpoint: returns {"status":"success","query":"<ip>",...} over plain HTTP. */
    const val EXIT_IP_URL = "http://ip-api.com/json/?fields=status,query,countryCode"
    const val DIRECT_IP_URL = "https://api.ipify.org"

    data class ProxyCandidate(
        val host: String,
        val port: Int,
        val source: String
    )

    data class RelayProbeResult(
        val candidate: ProxyCandidate,
        val latencyMs: Int,
        val exitIp: String?,
        val countryCode: String?
    ) {
        val works: Boolean get() = latencyMs > 0
        val isAnonymous: Boolean get() = exitIp != null && exitIp.isNotBlank()
    }

    sealed interface RelayPhase {
        data object Idle : RelayPhase
        data object Fetching : RelayPhase
        data class Probing(val total: Int, val done: Int) : RelayPhase
        data class Ready(val results: List<RelayProbeResult>) : RelayPhase
        data class Routed(val result: RelayProbeResult) : RelayPhase
        data class Failed(val message: String) : RelayPhase
    }

    /**
     * Parses SOCKS5 feed text: accepts "ip:port" per line, tolerates monosans' trailing
     * metadata ("ip:port|cc|..."), and ignores comments/blanks/malformed lines.
     * Returns deduped candidates capped at [maxCandidates].
     */
    fun parseSocks5Feed(raw: String, maxCandidates: Int = MAX_CANDIDATES): List<ProxyCandidate> {
        val seen = ConcurrentHashMap.newKeySet<String>()
        val out = ArrayList<ProxyCandidate>()
        val lineRegex = Regex("^(\\d{1,3}(?:\\.\\d{1,3}){3}):(\\d{2,5})")
        raw.lineSequence().forEach { rawLine ->
            if (out.size >= maxCandidates) return@forEach
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) return@forEach
            val match = lineRegex.find(line) ?: return@forEach
            val host = match.groupValues[1]
            val port = match.groupValues[2].toIntOrNull() ?: return@forEach
            if (port !in 1..65535) return@forEach
            // Reject malformed IPv4 octets like 999.1.1.1
            val octetsOk = host.split('.').all { it.toIntOrNull() in 0..255 }
            if (!octetsOk) return@forEach
            val key = "$host:$port"
            if (seen.add(key)) {
                out.add(ProxyCandidate(host, port, source = "public-feed"))
            }
        }
        return out
    }

    /**
     * Abstraction over the SOCKS transport so tests can drive the prober without network.
     * The production factory creates a java.net.Socket bound to a SOCKS5 proxy — the JDK
     * performs the real SOCKS5 handshake (RFC 1928, no-auth) on connect.
     */
    interface SocksSocketFactory {
        fun createSocksSocket(host: String, port: Int, connectTimeoutMs: Int): Socket
    }

    object DefaultSocksSocketFactory : SocksSocketFactory {
        override fun createSocksSocket(host: String, port: Int, connectTimeoutMs: Int): Socket {
            val proxyAddress = InetSocketAddress(host, port)
            val proxy = Proxy(Proxy.Type.SOCKS, proxyAddress as SocketAddress)
            return Socket(proxy)
        }
    }

    /**
     * Probes one candidate: connects through SOCKS5 to the exit-IP endpoint, reads the JSON,
     * verifies the answer and reports latency + exit IP. Returns null when the proxy is dead.
     */
    suspend fun probeCandidate(
        candidate: ProxyCandidate,
        timeoutMs: Int = PROBE_TIMEOUT_MS,
        socketFactory: SocksSocketFactory = DefaultSocksSocketFactory
    ): RelayProbeResult? {
        val started = System.currentTimeMillis()
        var socket: Socket? = null
        return try {
            socket = socketFactory.createSocksSocket(candidate.host, candidate.port, timeoutMs)
            socket.soTimeout = timeoutMs
            socket.connect(InetSocketAddress("ip-api.com", 80), timeoutMs)
            val request = "GET /json/?fields=status,query,countryCode HTTP/1.1\r\n" +
                "Host: ip-api.com\r\n" +
                "User-Agent: SAB-RelayProbe/1.0\r\n" +
                "Connection: close\r\n" +
                "\r\n"
            socket.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
            socket.getOutputStream().flush()
            val reader = BufferedInputStream(socket.getInputStream())
            val head = readHttpResponseHead(reader) ?: return null
            if (!head.startsWith("HTTP/1.") || !head.contains(" 200")) return null
            val body = readHttpBody(reader) ?: return null
            val exitIp = Regex("\"query\"\\s*:\\s*\"([0-9a-fA-F.:]+)\"").find(body)?.groupValues?.getOrNull(1)
            val country = Regex("\"countryCode\"\\s*:\\s*\"([A-Z]{2})\"").find(body)?.groupValues?.getOrNull(1)
            if (exitIp.isNullOrBlank() && !body.contains("\"status\":\"success\"")) return null
            val latency = (System.currentTimeMillis() - started).toInt()
            RelayProbeResult(candidate, latency, exitIp, country)
        } catch (t: SocketTimeoutException) {
            null
        } catch (t: IOException) {
            null
        } catch (t: SecurityException) {
            null
        } catch (t: IllegalArgumentException) {
            null
        } finally {
            runCatching { socket?.close() }
        }
    }

    /** Fetches the device's own public IP (direct connection) for the anonymity comparison. */
    suspend fun fetchDirectIp(
        timeoutMs: Int = PROBE_TIMEOUT_MS,
        connectionFactory: (String) -> java.net.HttpURLConnection = { url ->
            (java.net.URL(url).openConnection() as java.net.HttpURLConnection)
        }
    ): String? {
        var connection: java.net.HttpURLConnection? = null
        return try {
            connection = connectionFactory(DIRECT_IP_URL).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("User-Agent", "SAB-RelayProbe/1.0")
            }
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }.trim()
            if (Regex("^([0-9]{1,3}(?:\\.[0-9]{1,3}){3})$").matches(body) ||
                body.contains(":") // IPv6 passthrough
            ) body else null
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun readHttpResponseHead(input: BufferedInputStream, maxBytes: Int = 16 * 1024): String? {
        val head = StringBuilder()
        var last = -1
        var current = input.read()
        var count = 0
        while (current != -1 && count < maxBytes) {
            head.append(current.toChar())
            if (last == '\r'.code && current == '\n'.code && head.endsWith("\r\n\r\n")) {
                return head.toString()
            }
            last = current
            current = input.read()
            count++
        }
        return if (head.isNotEmpty()) head.toString() else null
    }

    private fun readHttpBody(input: BufferedInputStream, maxBytes: Int = 64 * 1024): String? {
        val body = StringBuilder()
        val buffer = ByteArray(4 * 1024)
        var total = 0
        while (total < maxBytes) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            body.append(String(buffer, 0, read, Charsets.UTF_8))
        }
        return if (body.isEmpty()) null else body.toString()
    }

    /** Best-first sorted shortlist shown in the confirmation prompt. */
    fun rankResults(results: List<RelayProbeResult>): List<RelayProbeResult> =
        results.filter { it.works }
            .sortedWith(
                compareByDescending<RelayProbeResult> { it.isAnonymous }
                    .thenBy { it.latencyMs }
            )
            .take(TOP_RESULTS)
}
