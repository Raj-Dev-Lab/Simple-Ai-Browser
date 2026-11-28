package com.example

import com.simpleaibrowser.sab.core.browser.SitePermissionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SitePermissionStoreTest {

    private val saved = mutableListOf<SitePermissionStore.SitePermission>()
    private val revoked = mutableListOf<Pair<String, String>>()

    @Before
    fun setUp() {
        SitePermissionStore.setPersistenceHooks(
            saver = { saved.add(it) },
            revoker = { origin, permission -> revoked.add(origin to permission) }
        )
        SitePermissionStore.replaceAll(emptyList())
    }

    @After
    fun tearDown() {
        SitePermissionStore.setPersistenceHooks(null, null)
        SitePermissionStore.replaceAll(emptyList())
    }

    @Test
    fun `unknown site returns null and must be prompted`() {
        assertNull(SitePermissionStore.decisionFor("example.com", "geolocation"))
    }

    @Test
    fun `record stores a decision and fires the saver`() {
        SitePermissionStore.record("Example.COM", "geolocation", true)
        assertTrue(SitePermissionStore.decisionFor("example.com", "geolocation") == true)
        assertEquals(1, saved.size)
        assertEquals("example.com", saved.first().origin)
        assertEquals("geolocation", saved.first().permission)
        assertTrue(saved.first().allow)
    }

    @Test
    fun `a recorded deny blocks without prompting`() {
        SitePermissionStore.record("videos.example", "camera", false)
        assertFalse(SitePermissionStore.decisionFor("videos.example", "camera") == true)
        assertTrue(SitePermissionStore.decisionFor("videos.example", "camera") == false)
    }

    @Test
    fun `revoke forgets a single decision and fires the revoker`() {
        SitePermissionStore.record("site.org", "notifications", true)
        SitePermissionStore.revoke("site.org", "notifications")
        assertNull(SitePermissionStore.decisionFor("site.org", "notifications"))
        assertEquals(listOf("site.org" to "notifications"), revoked)
    }

    @Test
    fun `revokeAllForOrigin clears only that origin`() {
        SitePermissionStore.record("a.com", "camera", true)
        SitePermissionStore.record("a.com", "geolocation", false)
        SitePermissionStore.record("b.com", "camera", true)
        SitePermissionStore.revokeAllForOrigin("a.com")
        assertNull(SitePermissionStore.decisionFor("a.com", "camera"))
        assertNull(SitePermissionStore.decisionFor("a.com", "geolocation"))
        assertTrue(SitePermissionStore.decisionFor("b.com", "camera") == true)
        assertEquals(listOf("a.com" to "*"), revoked)
    }

    @Test
    fun `clearAll wipes every decision`() {
        SitePermissionStore.record("a.com", "camera", true)
        SitePermissionStore.record("b.com", "microphone", false)
        SitePermissionStore.clearAll()
        assertNull(SitePermissionStore.decisionFor("a.com", "camera"))
        assertNull(SitePermissionStore.decisionFor("b.com", "microphone"))
        assertEquals(listOf("*" to "*"), revoked)
    }

    @Test
    fun `replaceAll restores a persisted snapshot`() {
        SitePermissionStore.replaceAll(
            listOf(
                SitePermissionStore.SitePermission("restored.com", "geolocation", true, 1L),
                SitePermissionStore.SitePermission("restored.com", "camera", false, 2L)
            )
        )
        assertTrue(SitePermissionStore.decisionFor("restored.com", "geolocation") == true)
        assertTrue(SitePermissionStore.decisionFor("restored.com", "camera") == false)
    }

    @Test
    fun `blank origins and permissions are ignored`() {
        SitePermissionStore.record("", "camera", true)
        SitePermissionStore.record("x.com", " ", true)
        assertNull(SitePermissionStore.decisionFor("", "camera"))
        assertNull(SitePermissionStore.decisionFor("x.com", " "))
        assertEquals(0, saved.size)
    }
}
