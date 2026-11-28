package com.example

import com.simpleaibrowser.sab.core.browser.CookieIsolationMode
import com.simpleaibrowser.sab.core.browser.DnsOverHttpsMode
import com.simpleaibrowser.sab.core.browser.IncognitoShield
import com.simpleaibrowser.sab.core.browser.PrivacyConfig
import com.simpleaibrowser.sab.core.browser.TrackingProtectionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncognitoShieldTest {

    private val laxUserConfig = PrivacyConfig(
        trackingProtectionLevel = TrackingProtectionLevel.STANDARD,
        doNotTrackEnabled = false,
        globalPrivacyControlEnabled = false,
        cookieIsolationMode = CookieIsolationMode.BLOCK_TRACKERS,
        cookiePurgingEnabled = false,
        stripTrackingParams = false,
        bounceTrackingProtection = false,
        fingerprintingDefense = false,
        localNetworkProtection = false,
        safeBrowsingEnabled = false,
        httpsOnlyEnabled = false,
        dnsOverHttpsMode = DnsOverHttpsMode.OFF
    )

    @Test
    fun `shielded config forces strict defenses regardless of user settings`() {
        val shielded = IncognitoShield.shieldedConfig(laxUserConfig, winner = null)
        assertEquals(TrackingProtectionLevel.STRICT, shielded.trackingProtectionLevel)
        assertTrue(shielded.doNotTrackEnabled)
        assertTrue(shielded.globalPrivacyControlEnabled)
        assertEquals(CookieIsolationMode.TOTAL_ISOLATE, shielded.cookieIsolationMode)
        assertTrue(shielded.cookiePurgingEnabled)
        assertTrue(shielded.stripTrackingParams)
        assertTrue(shielded.bounceTrackingProtection)
        assertTrue(shielded.fingerprintingDefense)
        assertTrue(shielded.localNetworkProtection)
        assertTrue(shielded.safeBrowsingEnabled)
        assertTrue(shielded.httpsOnlyEnabled)
    }

    @Test
    fun `benchmark winner replaces DoH and enables strict mode`() {
        val winner = IncognitoShield.BenchmarkWinner(DnsOverHttpsMode.QUAD9, "Quad9 Security", 42)
        val shielded = IncognitoShield.shieldedConfig(laxUserConfig, winner)
        assertEquals(DnsOverHttpsMode.QUAD9, shielded.dnsOverHttpsMode)
        assertTrue(shielded.dohStrict)
    }

    @Test
    fun `without a winner the user DoH is preserved not disabled`() {
        val withDoh = laxUserConfig.copy(dnsOverHttpsMode = DnsOverHttpsMode.ADGUARD)
        val shielded = IncognitoShield.shieldedConfig(withDoh, winner = null)
        assertEquals(DnsOverHttpsMode.ADGUARD, shielded.dnsOverHttpsMode)
        assertEquals(withDoh.dohStrict, shielded.dohStrict)

        val off = IncognitoShield.shieldedConfig(laxUserConfig, winner = null)
        assertEquals(DnsOverHttpsMode.OFF, off.dnsOverHttpsMode)
    }

    @Test
    fun `valid DoH json answers are recognized`() {
        assertTrue(
            IncognitoShield.isDohJsonAnswer(
                """{"Status":0,"TC":false,"RD":true,"RA":true,"AD":false,"Answer":[{"name":"example.com.","type":1,"TTL":3600,"data":"93.184.216.34"}]}"""
            )
        )
        assertFalse(IncognitoShield.isDohJsonAnswer("""{"Status":2,"Comment":"SERVFAIL"}"""))
        assertFalse(IncognitoShield.isDohJsonAnswer("<html>blocked</html>"))
        assertFalse(IncognitoShield.isDohJsonAnswer(""))
    }

    @Test
    fun `provider list covers quad9 adguard and cloudflare`() {
        val modes = IncognitoShield.DOH_PROVIDERS.map { it.mode }
        assertTrue(modes.contains(DnsOverHttpsMode.QUAD9))
        assertTrue(modes.contains(DnsOverHttpsMode.ADGUARD))
        assertTrue(modes.contains(DnsOverHttpsMode.CLOUDFLARE))
        IncognitoShield.DOH_PROVIDERS.forEach { spec ->
            assertTrue("provider ${spec.label} needs probe urls", spec.probeUrls.isNotEmpty())
            IncognitoShield.DOH_PROVIDERS.forEach { other ->
                if (other.mode != spec.mode) {
                    // Resolver URIs must stay distinct so the benchmark picks a real winner
                    assertTrue(spec.mode.resolverUri != other.mode.resolverUri)
                }
            }
        }
        assertNotNull(IncognitoShield.DOH_PROVIDERS.firstOrNull { it.mode == DnsOverHttpsMode.QUAD9 })
        assertNull(IncognitoShield.DOH_PROVIDERS.firstOrNull { it.mode == DnsOverHttpsMode.CUSTOM })
    }
}
