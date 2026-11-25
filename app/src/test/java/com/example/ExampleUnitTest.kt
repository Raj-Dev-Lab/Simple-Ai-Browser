package com.example

import com.simpleaibrowser.sab.core.browser.AdBlockFilterList
import com.simpleaibrowser.sab.core.browser.SAB_START_URL
import com.simpleaibrowser.sab.core.browser.SearchEngine
import com.simpleaibrowser.sab.core.browser.UrlResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `url resolver handles domains, https upgrades, and search queries`() {
        assertEquals(SAB_START_URL, UrlResolver.resolve("", SearchEngine.DUCKDUCKGO))
        assertEquals("https://mozilla.org", UrlResolver.resolve("mozilla.org", SearchEngine.DUCKDUCKGO))
        assertEquals("https://example.com/path", UrlResolver.resolve("http://example.com/path", SearchEngine.DUCKDUCKGO, httpsOnly = true))
        assertEquals("http://localhost:8080", UrlResolver.resolve("localhost:8080", SearchEngine.DUCKDUCKGO, httpsOnly = true))
        assertEquals(
            "https://duckduckgo.com/?q=geckoview+android",
            UrlResolver.resolve("geckoview android", SearchEngine.DUCKDUCKGO)
        )
    }

    @Test
    fun `ad block filter list detects tracker domains accurately`() {
        assertTrue(AdBlockFilterList.isBlockedHost("doubleclick.net"))
        assertTrue(AdBlockFilterList.isBlockedHost("sub.googlesyndication.com"))
        assertFalse(AdBlockFilterList.isBlockedHost("developer.mozilla.org"))
        assertFalse(AdBlockFilterList.isBlockedHost("github.com"))
    }
}
