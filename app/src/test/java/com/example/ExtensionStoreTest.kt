package com.example

import com.simpleaibrowser.sab.core.browser.ExtensionSource
import com.simpleaibrowser.sab.core.browser.ExtensionVersionComparator
import com.simpleaibrowser.sab.data.extensions.ExtensionStoreClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExtensionStoreTest {

    @Test
    fun `version comparator handles semver, prefixes, calendar tags, and prereleases`() {
        assertTrue(ExtensionVersionComparator.isNewer("1.3.0", "1.2.1"))
        assertTrue(ExtensionVersionComparator.isNewer("v2.0.1", "2.0.0"))
        assertEquals(0, ExtensionVersionComparator.compare("v1.5.0", "1.5.0"))
        assertTrue(ExtensionVersionComparator.isNewer("2026.11.20", "2026.11.2"))
        assertTrue(ExtensionVersionComparator.isNewer("1.6.0", "1.6.0-beta1"))
        assertFalse(ExtensionVersionComparator.isNewer("1.6.0-rc1", "1.6.0"))
        assertTrue(ExtensionVersionComparator.isNewer("1.6.0-rc2", "1.6.0-rc1"))
        assertTrue(ExtensionVersionComparator.isNewer("1.6.0b2", "1.6.0b1"))
        assertFalse(ExtensionVersionComparator.isNewer("", "1.0.0"))
        assertTrue(ExtensionVersionComparator.isNewer("1.0.0", ""))
    }

    @Test
    fun `extractOwnerRepoFromInput parses slugs, github urls, and rejects invalid inputs`() {
        assertEquals("gorhill/uBlock", ExtensionStoreClient.extractOwnerRepoFromInput("gorhill/uBlock"))
        assertEquals(
            "darkreader/darkreader",
            ExtensionStoreClient.extractOwnerRepoFromInput("https://github.com/darkreader/darkreader")
        )
        assertEquals(
            "darkreader/darkreader",
            ExtensionStoreClient.extractOwnerRepoFromInput("https://github.com/darkreader/darkreader.git")
        )
        assertEquals(
            "bitwarden/clients",
            ExtensionStoreClient.extractOwnerRepoFromInput("https://github.com/bitwarden/clients/releases/tag/v1.0.0")
        )
        assertNull(ExtensionStoreClient.extractOwnerRepoFromInput(""))
        assertNull(ExtensionStoreClient.extractOwnerRepoFromInput("ublock origin"))
        assertNull(ExtensionStoreClient.extractOwnerRepoFromInput("https://addons.mozilla.org/en-US/android/"))
    }

    @Test
    fun `parseAmoSearchJson extracts valid items, strips html, and skips entries without xpi`() {
        val sampleJson = """
            {
              "results": [
                {
                  "guid": "uBlock0@raymondhill.net",
                  "slug": "ublock-origin",
                  "name": {"en-US": "uBlock Origin"},
                  "summary": {"en-US": "Finally, an <b>efficient</b> blocker."},
                  "description": {"en-US": "<p>Low memory &amp; CPU.</p>"},
                  "authors": [{"name": "Raymond Hill"}],
                  "icon_url": "https://addons.mozilla.org/user-media/addon_icons/607/607454-64.png",
                  "homepage": {"en-US": "https://github.com/gorhill/uBlock"},
                  "average_daily_users": 8500000,
                  "is_recommended": true,
                  "ratings": {"average": 4.8, "count": 21000},
                  "last_updated": "2026-11-01T12:00:00Z",
                  "current_version": {
                    "version": "1.61.2",
                    "file": {
                      "url": "https://addons.mozilla.org/firefox/downloads/file/123/ublock.xpi",
                      "size": 3456789,
                      "permissions": ["<all_urls>", "storage", "webRequest"]
                    }
                  }
                },
                {
                  "guid": "missing-file@example.com",
                  "slug": "missing-file",
                  "name": {"en-US": "Broken Entry"},
                  "current_version": { "version": "1.0.0" }
                }
              ]
            }
        """.trimIndent()

        val items = ExtensionStoreClient.parseAmoSearchJson(sampleJson)
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("uBlock0@raymondhill.net", item.id)
        assertEquals("ublock-origin", item.slug)
        assertEquals("uBlock Origin", item.name)
        assertEquals("Finally, an efficient blocker.", item.summary)
        assertEquals("Low memory & CPU.", item.description)
        assertEquals("Raymond Hill", item.author)
        assertEquals("1.61.2", item.version)
        assertEquals(ExtensionSource.AMO, item.source)
        assertTrue(item.isRecommended)
        assertTrue(item.hasInstallableXpi)
        assertEquals(listOf("<all_urls>", "storage", "webRequest"), item.permissions)
    }

    @Test
    fun `parseGitHubSearchJson filters archived repos and maps metadata`() {
        val sampleJson = """
            {
              "items": [
                {
                  "full_name": "nicbarker/sample-ext",
                  "name": "sample-ext",
                  "description": "Fast reader helper",
                  "archived": false,
                  "stargazers_count": 420,
                  "html_url": "https://github.com/nicbarker/sample-ext",
                  "pushed_at": "2026-11-10T00:00:00Z",
                  "owner": {
                    "login": "nicbarker",
                    "avatar_url": "https://avatars.githubusercontent.com/u/1?v=4"
                  }
                },
                {
                  "full_name": "old/archived-ext",
                  "name": "archived-ext",
                  "archived": true,
                  "stargazers_count": 900
                }
              ]
            }
        """.trimIndent()

        val repos = ExtensionStoreClient.parseGitHubSearchJson(sampleJson)
        assertEquals(1, repos.size)
        val repo = repos.first()
        assertEquals("nicbarker/sample-ext", repo.id)
        assertEquals(ExtensionSource.GITHUB, repo.source)
        assertEquals(420L, repo.dailyUsersOrStars)
        assertFalse(repo.hasInstallableXpi)
    }

    @Test
    fun `parseGitHubReleasesJson skips drafts and prioritizes signed android firefox xpis`() {
        val sampleJson = """
            [
              {
                "tag_name": "v2.1.0-draft",
                "draft": true,
                "assets": [
                  {"name": "ext-android-signed.xpi", "browser_download_url": "https://github.com/a/b/releases/download/draft/ext.xpi", "size": 100}
                ]
              },
              {
                "tag_name": "v2.0.0",
                "name": "Release 2.0.0",
                "draft": false,
                "body": "Bug fixes",
                "published_at": "2026-11-12T08:00:00Z",
                "assets": [
                  {"name": "readme.txt", "browser_download_url": "https://github.com/a/b/releases/download/v2.0.0/readme.txt", "size": 12},
                  {"name": "ext-unsigned.xpi", "browser_download_url": "https://github.com/a/b/releases/download/v2.0.0/ext-unsigned.xpi", "size": 500},
                  {"name": "ext-firefox.xpi", "browser_download_url": "https://github.com/a/b/releases/download/v2.0.0/ext-firefox.xpi", "size": 520},
                  {"name": "ext-android-signed.xpi", "browser_download_url": "https://github.com/a/b/releases/download/v2.0.0/ext-android-signed.xpi", "size": 540}
                ]
              }
            ]
        """.trimIndent()

        val assets = ExtensionStoreClient.parseGitHubReleasesJson(sampleJson)
        assertEquals(3, assets.size)
        assertEquals("ext-android-signed.xpi", assets[0].name)
        assertEquals("ext-firefox.xpi", assets[1].name)
        assertEquals("ext-unsigned.xpi", assets[2].name)
        assertEquals("v2.0.0", assets[0].releaseTag)
    }
}
