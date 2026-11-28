package com.example

import com.simpleaibrowser.sab.core.browser.SecureRouting
import com.simpleaibrowser.sab.core.network.PublicProxyRelay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicProxyRelayTest {

    @Test
    fun `parses plain ip port feeds`() {
        val raw = """
            # comment line
            1.2.3.4:1080
            5.6.7.8:9050

            invalid-line
            9.9.9.9:notaport
            300.1.1.1:8080
            10.0.0.7:5678
        """.trimIndent()
        val parsed = PublicProxyRelay.parseSocks5Feed(raw)
        assertEquals(3, parsed.size)
        assertEquals("1.2.3.4", parsed[0].host)
        assertEquals(1080, parsed[0].port)
        assertEquals("5.6.7.8", parsed[1].host)
        assertEquals("10.0.0.7", parsed[2].host)
    }

    @Test
    fun `parses monosans style metadata suffixes`() {
        val raw = """
            11.22.33.44:1080|US|Socks5|elite|86400
            55.66.77.88:9050|DE|Socks5|anonymous|3600
        """.trimIndent()
        val parsed = PublicProxyRelay.parseSocks5Feed(raw)
        assertEquals(2, parsed.size)
        assertEquals(1080, parsed[0].port)
        assertEquals(9050, parsed[1].port)
    }

    @Test
    fun `deduplicates and caps the candidate list`() {
        val raw = (1..20).joinToString("\n") { "10.0.0.$it:1080" } + "\n" + "10.0.0.1:1080\n10.0.0.2:1080"
        val parsed = PublicProxyRelay.parseSocks5Feed(raw)
        assertEquals(20, parsed.size)
        val tiny = PublicProxyRelay.parseSocks5Feed(raw, maxCandidates = 5)
        assertEquals(5, tiny.size)
    }

    @Test
    fun `ranking prefers anonymous then latency and caps results`() {
        val mk = { host: String, latency: Int, ip: String? ->
            PublicProxyRelay.RelayProbeResult(
                PublicProxyRelay.ProxyCandidate(host, 1080, "test"),
                latency,
                ip,
                null
            )
        }
        val ranked = PublicProxyRelay.rankResults(
            listOf(
                mk("slow-anon", 900, "1.1.1.1"),
                mk("fast-anon", 100, "2.2.2.2"),
                mk("dead", 0, null) // latency 0 => never answered => filtered out
            )
        )
        assertEquals(2, ranked.size)
        assertEquals("fast-anon", ranked[0].candidate.host)
        assertEquals("slow-anon", ranked[1].candidate.host)
        // A candidate that answered but exposed no exit IP still counts as working
        // (rankResults treats latency > 0 as working) and sorts below anonymous ones.
        val withNoIp = PublicProxyRelay.rankResults(
            listOf(mk("no-ip", 100, null), mk("anon", 200, "3.3.3.3"))
        )
        assertEquals("anon", withNoIp[0].candidate.host)
        assertEquals("no-ip", withNoIp[1].candidate.host)
    }

    @Test
    fun `routing config maps endpoints for every mode`() {
        val tor = SecureRouting.endpointFor(
            SecureRouting.RoutingConfig(SecureRouting.RoutingMode.TOR_ORBOT, "", 0)
        )
        assertEquals("127.0.0.1", tor.first)
        assertEquals(9050, tor.second)

        val custom = SecureRouting.endpointFor(
            SecureRouting.RoutingConfig(SecureRouting.RoutingMode.SOCKS5, " 192.168.1.10 ", 1080)
        )
        assertEquals("192.168.1.10", custom.first)
        assertEquals(1080, custom.second)

        assertEquals("" to 0, SecureRouting.endpointFor(
            SecureRouting.RoutingConfig(SecureRouting.RoutingMode.OFF, "ignored", 9999)
        ))
    }

    @Test
    fun `gecko prefs file routes socks and dns through the tunnel`() {
        val content = SecureRouting.geckoPrefsFileContent(
            SecureRouting.RoutingConfig(SecureRouting.RoutingMode.TOR_ORBOT, "", 0)
        )
        assertNotNull(content)
        assertTrue(content!!.contains("user_pref(\"network.proxy.type\", 1);"))
        assertTrue(content.contains("user_pref(\"network.proxy.socks\", \"127.0.0.1\");"))
        assertTrue(content.contains("user_pref(\"network.proxy.socks_port\", 9050);"))
        assertTrue(content.contains("user_pref(\"network.proxy.socks_version\", 5);"))
        assertTrue(content.contains("user_pref(\"network.proxy.socks_remote_dns\", true);"))
        assertTrue(content.contains("user_pref(\"network.proxy.no_proxies_on\", \"\");"))
    }

    @Test
    fun `off mode produces no prefs file content`() {
        assertNull(
            SecureRouting.geckoPrefsFileContent(
                SecureRouting.RoutingConfig(SecureRouting.RoutingMode.OFF, "1.2.3.4", 1080)
            )
        )
        assertNull(
            SecureRouting.geckoPrefsFileContent(
                SecureRouting.RoutingConfig(SecureRouting.RoutingMode.SOCKS5, "1.2.3.4", 0)
            )
        )
    }

    @Test
    fun `okhttp proxy is only created for valid endpoints`() {
        assertNull(
            SecureRouting.okHttpProxy(
                SecureRouting.RoutingConfig(SecureRouting.RoutingMode.OFF, "", 0)
            )
        )
        val proxy = SecureRouting.okHttpProxy(
            SecureRouting.RoutingConfig(SecureRouting.RoutingMode.SOCKS5, "127.0.0.1", 9050)
        )
        assertTrue(proxy != null)
        assertEquals(java.net.Proxy.Type.SOCKS, proxy!!.type())
    }
}
