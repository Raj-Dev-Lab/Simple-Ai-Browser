package com.simpleaibrowser.sab.core.browser

import android.graphics.Bitmap
import android.view.View
import kotlinx.coroutines.flow.StateFlow

enum class ExtensionSource(val id: String, val displayName: String) {
    AMO("amo", "Mozilla Add-ons"),
    GITHUB("github", "GitHub"),
    DIRECT_URL("direct", "Direct .xpi"),
    BUILTIN("builtin", "Built-in");

    companion object {
        fun fromId(id: String): ExtensionSource = entries.firstOrNull { it.id == id } ?: AMO
    }
}

enum class AmoCategory(val id: String, val label: String, val apiSort: String, val apiCategorySlug: String?) {
    FEATURED("featured", "Featured", "users", null),
    PRIVACY("privacy", "Privacy & Security", "users", "privacy-security"),
    APPEARANCE("appearance", "Dark & Themes", "users", "appearance"),
    PRODUCTIVITY("productivity", "Productivity", "users", "tabs"),
    MEDIA("media", "Video & Audio", "users", "photos-music-videos")
}

data class ExtensionCatalogItem(
    val id: String,
    val slug: String,
    val name: String,
    val summary: String,
    val description: String,
    val author: String,
    val version: String,
    val downloadUrl: String,
    val iconUrl: String?,
    val homepageUrl: String?,
    val sourceRepo: String?,
    val source: ExtensionSource,
    val rating: Double = 0.0,
    val ratingCount: Int = 0,
    val dailyUsersOrStars: Long = 0L,
    val isRecommended: Boolean = false,
    val permissions: List<String> = emptyList(),
    val updatedAt: String? = null,
    val releaseAssetName: String? = null,
    val releaseAssetSizeBytes: Long = -1L,
    val hasInstallableXpi: Boolean = downloadUrl.isNotBlank()
)

data class GitHubReleaseXpiAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val releaseTag: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val publishedAt: String
)

data class ExtensionActionState(
    val extensionId: String,
    val extensionName: String,
    val title: String,
    val badgeText: String?,
    val badgeBackgroundColor: Int?,
    val badgeTextColor: Int?,
    val enabled: Boolean,
    val iconBitmap: Bitmap?
)

data class InstalledExtensionInfo(
    val id: String,
    val name: String,
    val description: String,
    val fullDescription: String,
    val version: String,
    val author: String,
    val iconBitmap: Bitmap? = null,
    val iconUrl: String? = null,
    val enabled: Boolean,
    val allowedInPrivateBrowsing: Boolean,
    val optionsPageUrl: String? = null,
    val openOptionsInTab: Boolean = true,
    val homepageUrl: String? = null,
    val isBuiltIn: Boolean = false,
    val disabledReason: String? = null,
    val permissions: List<String> = emptyList(),
    val source: ExtensionSource = ExtensionSource.AMO,
    val sourceRef: String = id,
    val autoUpdateEnabled: Boolean = true,
    val availableUpdateVersion: String? = null,
    val availableUpdateUrl: String? = null,
    val lastCheckedAt: Long = 0L,
    val actionState: ExtensionActionState? = null
)

sealed interface ExtensionInstallState {
    data object Idle : ExtensionInstallState
    data class Installing(val name: String) : ExtensionInstallState
    data class CheckingUpdates(val progressLabel: String) : ExtensionInstallState
    data class Success(val message: String) : ExtensionInstallState
    data class Error(val message: String) : ExtensionInstallState
}

interface ExtensionPopupSession {
    val extensionId: String
    val title: String
    fun createPopupView(context: android.content.Context): View
    fun release()
}

interface BrowserExtensionsController {
    val installedExtensions: StateFlow<List<InstalledExtensionInfo>>
    val activePopup: StateFlow<ExtensionPopupSession?>

    suspend fun refreshInstalled(): List<InstalledExtensionInfo>
    suspend fun installFromUrl(url: String, installMethod: String? = null): Result<InstalledExtensionInfo>
    suspend fun enableExtension(extensionId: String): Result<InstalledExtensionInfo>
    suspend fun disableExtension(extensionId: String): Result<InstalledExtensionInfo>
    suspend fun setAllowedInPrivateBrowsing(extensionId: String, allowed: Boolean): Result<InstalledExtensionInfo>
    suspend fun uninstallExtension(extensionId: String): Result<Unit>
    suspend fun updateViaNativeManager(extensionId: String): Result<InstalledExtensionInfo?>
    fun triggerBrowserAction(extensionId: String)
    fun openOptionsPage(extensionId: String)
    fun dismissPopup()
}

/**
 * Compares extension version strings (SemVer, calendar tags like v2026.11.2, Mozilla dev/rc tags).
 * Returns > 0 if candidate is newer than current, < 0 if older, 0 if equivalent.
 */
object ExtensionVersionComparator {
    private val cleanPrefixRegex = Regex("^[vV]+")

    fun compare(candidate: String?, current: String?): Int {
        val aNorm = normalize(candidate)
        val bNorm = normalize(current)
        if (aNorm == bNorm) return 0
        if (aNorm.isEmpty()) return -1
        if (bNorm.isEmpty()) return 1

        val aParts = tokenize(aNorm)
        val bParts = tokenize(bNorm)
        val maxLen = maxOf(aParts.size, bParts.size)
        for (i in 0 until maxLen) {
            val aSeg = aParts.getOrNull(i)
            val bSeg = bParts.getOrNull(i)
            val cmp = compareSegments(aSeg, bSeg)
            if (cmp != 0) return cmp
        }
        return 0
    }

    fun isNewer(candidate: String?, current: String?): Boolean = compare(candidate, current) > 0

    private fun normalize(raw: String?): String {
        return raw.orEmpty().trim().replace(cleanPrefixRegex, "")
    }

    private fun tokenize(version: String): List<String> {
        return version.split('.', '-', '+', '_')
            .flatMap { splitAlphaNumeric(it) }
            .filter { it.isNotEmpty() }
    }

    private fun splitAlphaNumeric(segment: String): List<String> {
        if (segment.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        var lastIsDigit: Boolean? = null
        for (ch in segment) {
            val isDigit = ch.isDigit()
            if (lastIsDigit != null && lastIsDigit != isDigit) {
                out.add(buf.toString())
                buf.setLength(0)
            }
            buf.append(ch)
            lastIsDigit = isDigit
        }
        if (buf.isNotEmpty()) out.add(buf.toString())
        return out
    }

    private fun compareSegments(a: String?, b: String?): Int {
        if (a == b) return 0
        if (a == null) {
            // Trailing pre-release tag ("rc", "beta", "alpha") makes other side LOWER than clean base version
            return if (b != null && b.any { !it.isDigit() }) 1
            else if (b == "0") 0
            else -1
        }
        if (b == null) {
            return if (a.any { !it.isDigit() }) -1
            else if (a == "0") 0
            else 1
        }
        val aNum = a.toLongOrNull()
        val bNum = b.toLongOrNull()
        if (aNum != null && bNum != null) return aNum.compareTo(bNum)
        if (aNum != null) return 1
        if (bNum != null) return -1
        return a.lowercase().compareTo(b.lowercase())
    }
}
