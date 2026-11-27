package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.simpleaibrowser.sab.core.browser.CookieIsolationMode
import com.simpleaibrowser.sab.core.browser.DnsOverHttpsMode
import com.simpleaibrowser.sab.core.browser.TrackingProtectionLevel
import com.simpleaibrowser.sab.core.browser.UrlResolver
import com.simpleaibrowser.sab.data.BrowserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivacyAndViewerTest {

    @Test
    fun `stripTrackingParameters removes trackers while preserving real params and fragments`() {
        val dirty = "https://example.com/watch?v=dQw4w9WgXcQ&utm_source=newsletter&fbclid=IwAR123&gclid=abc#t=42"
        assertEquals("https://example.com/watch?v=dQw4w9WgXcQ#t=42", UrlResolver.stripTrackingParameters(dirty))

        val onlyTrackers = "https://news.site/article?utm_medium=social&igshid=xyz123"
        assertEquals("https://news.site/article", UrlResolver.stripTrackingParameters(onlyTrackers))

        val noTrackers = "https://duckduckgo.com/?q=privacy+browser&ia=web"
        assertEquals(noTrackers, UrlResolver.stripTrackingParameters(noTrackers))

        val internalUrl = "sab://start"
        assertEquals(internalUrl, UrlResolver.stripTrackingParameters(internalUrl))
    }

    @Test
    fun `isPdfTarget detects pdf extensions and mime types`() {
        assertTrue(UrlResolver.isPdfTarget("https://example.org/docs/paper.PDF?download=1", null))
        assertTrue(UrlResolver.isPdfTarget("https://example.org/stream?id=42", "application/pdf; charset=binary"))
        assertFalse(UrlResolver.isPdfTarget("https://example.org/video.mp4", "video/mp4"))
    }

    @Test
    fun `classifyPlayableMedia classifies video streams audio files and archives accurately`() {
        assertEquals(true to true, UrlResolver.classifyPlayableMedia("https://cdn.example.com/clip.mp4?token=1", null))
        assertEquals(true to true, UrlResolver.classifyPlayableMedia("https://live.example.com/master.m3u8", "application/vnd.apple.mpegurl"))
        assertEquals(true to false, UrlResolver.classifyPlayableMedia("https://cdn.example.com/track.flac", null))
        assertEquals(true to false, UrlResolver.classifyPlayableMedia("https://cdn.example.com/stream", "audio/mpeg"))
        assertEquals(false to false, UrlResolver.classifyPlayableMedia("https://cdn.example.com/archive.zip", "application/zip"))
    }

    @Test
    fun `BrowserPreferences persists and restores Privacy Suite settings`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = BrowserPreferences(context)

        prefs.setTrackingProtectionLevel(TrackingProtectionLevel.STRICT)
        prefs.setDoNotTrackEnabled(false)
        prefs.setGlobalPrivacyControlEnabled(true)
        prefs.setCookieIsolationMode(CookieIsolationMode.FIRST_PARTY_ONLY)
        prefs.setCookiePurgingEnabled(false)
        prefs.setStripTrackingParams(true)
        prefs.setBounceTrackingProtection(false)
        prefs.setFingerprintingDefense(true)
        prefs.setLocalNetworkProtection(true)
        prefs.setSafeBrowsingEnabled(false)
        prefs.setDnsOverHttpsConfig(DnsOverHttpsMode.CUSTOM, "https://dns.quad9.net/dns-query", strict = true)
        prefs.setClearPrivateDataOnExit(true)

        val saved = prefs.settingsFlow.first()
        assertEquals(TrackingProtectionLevel.STRICT, saved.trackingProtectionLevel)
        assertFalse(saved.doNotTrackEnabled)
        assertTrue(saved.globalPrivacyControlEnabled)
        assertEquals(CookieIsolationMode.FIRST_PARTY_ONLY, saved.cookieIsolationMode)
        assertFalse(saved.cookiePurgingEnabled)
        assertTrue(saved.stripTrackingParams)
        assertFalse(saved.bounceTrackingProtection)
        assertTrue(saved.fingerprintingDefense)
        assertTrue(saved.localNetworkProtection)
        assertFalse(saved.safeBrowsingEnabled)
        assertEquals(DnsOverHttpsMode.CUSTOM, saved.dnsOverHttpsMode)
        assertEquals("https://dns.quad9.net/dns-query", saved.customDohUrl)
        assertTrue(saved.dohStrict)
        assertTrue(saved.clearPrivateDataOnExit)
        assertEquals(TrackingProtectionLevel.STRICT, saved.toPrivacyConfig().trackingProtectionLevel)
    }
}
