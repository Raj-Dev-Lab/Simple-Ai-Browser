package com.simpleaibrowser.sab.data.extensions

import com.simpleaibrowser.sab.core.browser.AmoCategory
import com.simpleaibrowser.sab.core.browser.ExtensionCatalogItem
import com.simpleaibrowser.sab.core.browser.ExtensionSource
import com.simpleaibrowser.sab.core.browser.GitHubReleaseXpiAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

data class GitHubRepoDetail(
    val ownerRepo: String,
    val name: String,
    val description: String,
    val stars: Long,
    val homepageUrl: String?,
    val avatarUrl: String?,
    val xpiAssets: List<GitHubReleaseXpiAsset>
)

data class CachedResponse(
    val body: String,
    val fetchedAtMs: Long
)

class ExtensionStoreClient {

    private val responseCache = ConcurrentHashMap<String, CachedResponse>()

    suspend fun searchAmoExtensions(
        query: String,
        category: AmoCategory
    ): Result<List<ExtensionCatalogItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildString {
                append("https://addons.mozilla.org/api/v5/addons/search/?app=android&type=extension&page_size=24&lang=en-US")
                val q = query.trim()
                if (q.isNotEmpty()) {
                    append("&q=").append(URLEncoder.encode(q, StandardCharsets.UTF_8.name()))
                    append("&sort=relevance")
                } else {
                    append("&sort=").append(category.apiSort)
                    if (!category.apiCategorySlug.isNullOrBlank()) {
                        append("&category=").append(category.apiCategorySlug)
                    }
                }
            }
            val json = fetchJson(url, cacheTtlMs = 10 * 60_000L)
            parseAmoSearchJson(json)
        }
    }

    suspend fun fetchAmoAddon(guidOrSlug: String): Result<ExtensionCatalogItem> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(guidOrSlug.trim(), StandardCharsets.UTF_8.name())
            val url = "https://addons.mozilla.org/api/v5/addons/addon/$encoded/?app=android&lang=en-US"
            val json = fetchJson(url, cacheTtlMs = 5 * 60_000L)
            parseAmoAddonJson(JSONObject(json))
                ?: throw IOException("No Android-compatible release found on Mozilla Add-ons for $guidOrSlug")
        }
    }

    suspend fun searchGitHubExtensions(query: String): Result<List<ExtensionCatalogItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = query.trim()
            val ownerRepo = extractOwnerRepoFromInput(clean)
            if (ownerRepo != null && !clean.contains(" ")) {
                val detail = fetchGitHubRepoDetail(ownerRepo).getOrThrow()
                val primaryAsset = detail.xpiAssets.firstOrNull()
                return@runCatching listOf(
                    ExtensionCatalogItem(
                        id = detail.ownerRepo,
                        slug = detail.ownerRepo,
                        name = detail.name,
                        summary = detail.description.ifBlank { "GitHub repository ${detail.ownerRepo}" },
                        description = primaryAsset?.releaseNotes?.ifBlank { detail.description } ?: detail.description,
                        author = detail.ownerRepo.substringBefore('/'),
                        version = primaryAsset?.releaseTag ?: "Repo",
                        downloadUrl = primaryAsset?.downloadUrl.orEmpty(),
                        iconUrl = detail.avatarUrl,
                        homepageUrl = "https://github.com/${detail.ownerRepo}",
                        sourceRepo = detail.ownerRepo,
                        source = ExtensionSource.GITHUB,
                        dailyUsersOrStars = detail.stars,
                        updatedAt = primaryAsset?.publishedAt,
                        releaseAssetName = primaryAsset?.name,
                        releaseAssetSizeBytes = primaryAsset?.sizeBytes ?: -1L,
                        hasInstallableXpi = primaryAsset != null
                    )
                )
            }

            val ghQuery = if (clean.isEmpty()) {
                "topic:firefox-extension stars:>80"
            } else {
                "$clean (topic:firefox-extension OR topic:webextension OR firefox extension)"
            }
            val encoded = URLEncoder.encode(ghQuery, StandardCharsets.UTF_8.name())
            val url = "https://api.github.com/search/repositories?q=$encoded&sort=stars&order=desc&per_page=24"
            val json = fetchJson(url, cacheTtlMs = 15 * 60_000L, isGitHub = true)
            parseGitHubSearchJson(json)
        }
    }

    suspend fun fetchGitHubRepoDetail(rawInput: String): Result<GitHubRepoDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val ownerRepo = extractOwnerRepoFromInput(rawInput)
                ?: throw IOException("Enter a valid GitHub repository (e.g. owner/repo or https://github.com/owner/repo)")

            val repoUrl = "https://api.github.com/repos/$ownerRepo"
            val releasesUrl = "https://api.github.com/repos/$ownerRepo/releases?per_page=8"

            val repoJson = JSONObject(fetchJson(repoUrl, cacheTtlMs = 10 * 60_000L, isGitHub = true))
            val releasesJson = fetchJson(releasesUrl, cacheTtlMs = 5 * 60_000L, isGitHub = true)
            val assets = parseGitHubReleasesJson(releasesJson)

            GitHubRepoDetail(
                ownerRepo = repoJson.optString("full_name", ownerRepo),
                name = repoJson.optString("name", ownerRepo.substringAfter('/')),
                description = stripNull(repoJson.optString("description")).orEmpty(),
                stars = repoJson.optLong("stargazers_count", 0L),
                homepageUrl = stripNull(repoJson.optString("homepage"))?.takeIf { it.startsWith("http") }
                    ?: stripNull(repoJson.optString("html_url")),
                avatarUrl = repoJson.optJSONObject("owner")?.let { stripNull(it.optString("avatar_url")) },
                xpiAssets = assets
            )
        }
    }

    suspend fun resolveDirectXpiOrGitHub(rawUrl: String): Result<ExtensionCatalogItem> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = rawUrl.trim()
            if (trimmed.isBlank()) throw IOException("Enter a GitHub repo or .xpi link.")

            val lower = trimmed.lowercase()
            if (lower.endsWith(".xpi") || lower.contains(".xpi?")) {
                val fileName = trimmed.substringBefore('?').substringAfterLast('/').ifBlank { "extension.xpi" }
                return@runCatching ExtensionCatalogItem(
                    id = trimmed,
                    slug = fileName.removeSuffix(".xpi"),
                    name = fileName.removeSuffix(".xpi"),
                    summary = "Direct Mozilla .xpi package install",
                    description = trimmed,
                    author = trimmed.substringAfter("://").substringBefore('/'),
                    version = "Direct",
                    downloadUrl = trimmed,
                    iconUrl = null,
                    homepageUrl = trimmed,
                    sourceRepo = extractOwnerRepoFromInput(trimmed),
                    source = if (lower.contains("github.com") || lower.contains("githubusercontent.com")) {
                        ExtensionSource.GITHUB
                    } else {
                        ExtensionSource.DIRECT_URL
                    },
                    hasInstallableXpi = true
                )
            }

            val detail = fetchGitHubRepoDetail(trimmed).getOrThrow()
            val primaryAsset = detail.xpiAssets.firstOrNull()
                ?: throw IOException(
                    "No .xpi installer asset found in the latest GitHub releases of ${detail.ownerRepo}."
                )
            ExtensionCatalogItem(
                id = detail.ownerRepo,
                slug = detail.ownerRepo,
                name = detail.name,
                summary = detail.description.ifBlank { "GitHub release ${primaryAsset.releaseTag}" },
                description = primaryAsset.releaseNotes.ifBlank { detail.description },
                author = detail.ownerRepo.substringBefore('/'),
                version = primaryAsset.releaseTag,
                downloadUrl = primaryAsset.downloadUrl,
                iconUrl = detail.avatarUrl,
                homepageUrl = "https://github.com/${detail.ownerRepo}",
                sourceRepo = detail.ownerRepo,
                source = ExtensionSource.GITHUB,
                dailyUsersOrStars = detail.stars,
                updatedAt = primaryAsset.publishedAt,
                releaseAssetName = primaryAsset.name,
                releaseAssetSizeBytes = primaryAsset.sizeBytes,
                hasInstallableXpi = true
            )
        }
    }

    private fun fetchJson(
        urlString: String,
        cacheTtlMs: Long,
        isGitHub: Boolean = false
    ): String {
        val now = System.currentTimeMillis()
        val cached = responseCache[urlString]
        if (cached != null && (now - cached.fetchedAtMs) < cacheTtlMs) {
            return cached.body
        }

        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "SimpleAIBrowser-Android/1.3 (GeckoView)")
            setRequestProperty(
                "Accept",
                if (isGitHub) "application/vnd.github+json" else "application/json"
            )
            if (isGitHub) {
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
        }

        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                if (isGitHub && (code == 403 || code == 429)) {
                    throw IOException("GitHub API rate limit reached (HTTP $code). Please wait a few minutes and try again.")
                }
                if (code == 404) {
                    throw IOException("Not found (HTTP 404). Check the repository name or add-on ID.")
                }
                throw IOException("Store request failed (HTTP $code).")
            }
            responseCache[urlString] = CachedResponse(body, now)
            body
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        fun extractOwnerRepoFromInput(rawInput: String): String? {
            val trimmed = rawInput.trim().removeSuffix("/")
            if (trimmed.isEmpty()) return null

            val ghRegex = Regex(
                pattern = "(?:https?://)?(?:www\\.)?github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)(?:/.*)?",
                option = RegexOption.IGNORE_CASE
            )
            val urlMatch = ghRegex.matchEntire(trimmed)
            if (urlMatch != null) {
                val owner = urlMatch.groupValues[1]
                val repo = urlMatch.groupValues[2].removeSuffix(".git")
                if (owner.isNotBlank() && repo.isNotBlank()) return "$owner/$repo"
            }

            val slugRegex = Regex("^([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)$")
            val slugMatch = slugRegex.matchEntire(trimmed)
            if (slugMatch != null) {
                val owner = slugMatch.groupValues[1]
                val repo = slugMatch.groupValues[2].removeSuffix(".git")
                return "$owner/$repo"
            }
            return null
        }

        fun parseAmoSearchJson(rawJson: String): List<ExtensionCatalogItem> {
            val root = JSONObject(rawJson)
            val results = root.optJSONArray("results") ?: return emptyList()
            val out = mutableListOf<ExtensionCatalogItem>()
            for (i in 0 until results.length()) {
                val itemObj = results.optJSONObject(i) ?: continue
                parseAmoAddonJson(itemObj)?.let { out.add(it) }
            }
            return out
        }

        fun parseAmoAddonJson(obj: JSONObject): ExtensionCatalogItem? {
            val guid = stripNull(obj.optString("guid")).orEmpty()
            val slug = stripNull(obj.optString("slug")) ?: guid
            val currentVersion = obj.optJSONObject("current_version") ?: return null
            val fileObj = currentVersion.optJSONObject("file") ?: return null
            val downloadUrl = stripNull(fileObj.optString("url")).orEmpty()
            if (downloadUrl.isBlank()) return null

            val perms = mutableListOf<String>()
            val permsArray = fileObj.optJSONArray("permissions")
            if (permsArray != null) {
                for (i in 0 until permsArray.length()) {
                    stripNull(permsArray.optString(i))?.let { perms.add(it) }
                }
            }

            val authorsArray = obj.optJSONArray("authors")
            val firstAuthor = if (authorsArray != null && authorsArray.length() > 0) {
                stripNull(authorsArray.optJSONObject(0)?.optString("name"))
            } else {
                null
            } ?: "Mozilla Community"

            val ratingsObj = obj.optJSONObject("ratings")
            val name = extractLocalizedString(obj.opt("name")) ?: slug
            val summary = extractLocalizedString(obj.opt("summary")).orEmpty()
            val description = extractLocalizedString(obj.opt("description")) ?: summary
            val homepage = extractLocalizedString(obj.opt("homepage")) ?: stripNull(obj.optString("url"))

            return ExtensionCatalogItem(
                id = guid.ifBlank { slug },
                slug = slug,
                name = name,
                summary = stripHtml(summary),
                description = stripHtml(description),
                author = firstAuthor,
                version = stripNull(currentVersion.optString("version")).orEmpty(),
                downloadUrl = downloadUrl,
                iconUrl = stripNull(obj.optString("icon_url")),
                homepageUrl = homepage,
                sourceRepo = null,
                source = ExtensionSource.AMO,
                rating = ratingsObj?.optDouble("average", 0.0) ?: 0.0,
                ratingCount = ratingsObj?.optInt("count", 0) ?: 0,
                dailyUsersOrStars = obj.optLong("average_daily_users", 0L),
                isRecommended = obj.optBoolean("is_recommended", false),
                permissions = perms,
                updatedAt = stripNull(obj.optString("last_updated")),
                releaseAssetSizeBytes = fileObj.optLong("size", -1L),
                hasInstallableXpi = true
            )
        }

        fun parseGitHubSearchJson(rawJson: String): List<ExtensionCatalogItem> {
            val root = JSONObject(rawJson)
            val items = root.optJSONArray("items") ?: return emptyList()
            val out = mutableListOf<ExtensionCatalogItem>()
            for (i in 0 until items.length()) {
                val repo = items.optJSONObject(i) ?: continue
                if (repo.optBoolean("archived", false)) continue
                val fullName = stripNull(repo.optString("full_name")) ?: continue
                val name = stripNull(repo.optString("name")) ?: fullName.substringAfter('/')
                val description = stripNull(repo.optString("description")).orEmpty()
                val ownerObj = repo.optJSONObject("owner")
                val ownerLogin = ownerObj?.let { stripNull(it.optString("login")) } ?: fullName.substringBefore('/')
                val avatarUrl = ownerObj?.let { stripNull(it.optString("avatar_url")) }
                val htmlUrl = stripNull(repo.optString("html_url")) ?: "https://github.com/$fullName"

                out.add(
                    ExtensionCatalogItem(
                        id = fullName,
                        slug = fullName,
                        name = name,
                        summary = description.ifBlank { "GitHub WebExtension repository ($fullName)" },
                        description = description,
                        author = ownerLogin,
                        version = "Releases",
                        downloadUrl = "",
                        iconUrl = avatarUrl,
                        homepageUrl = htmlUrl,
                        sourceRepo = fullName,
                        source = ExtensionSource.GITHUB,
                        dailyUsersOrStars = repo.optLong("stargazers_count", 0L),
                        updatedAt = stripNull(repo.optString("pushed_at")),
                        hasInstallableXpi = false
                    )
                )
            }
            return out
        }

        fun parseGitHubReleasesJson(rawJson: String): List<GitHubReleaseXpiAsset> {
            val releases = JSONArray(rawJson)
            val assetsOut = mutableListOf<GitHubReleaseXpiAsset>()
            for (i in 0 until releases.length()) {
                val rel = releases.optJSONObject(i) ?: continue
                if (rel.optBoolean("draft", false)) continue
                val tag = stripNull(rel.optString("tag_name")).orEmpty()
                val relTitle = stripNull(rel.optString("name")).orEmpty().ifBlank { tag }
                val notes = stripNull(rel.optString("body")).orEmpty()
                val published = stripNull(rel.optString("published_at")).orEmpty()
                val assets = rel.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val asset = assets.optJSONObject(j) ?: continue
                    val name = stripNull(asset.optString("name")).orEmpty()
                    val dlUrl = stripNull(asset.optString("browser_download_url")).orEmpty()
                    if (name.endsWith(".xpi", ignoreCase = true) && dlUrl.startsWith("http")) {
                        assetsOut.add(
                            GitHubReleaseXpiAsset(
                                name = name,
                                downloadUrl = dlUrl,
                                sizeBytes = asset.optLong("size", -1L),
                                releaseTag = tag,
                                releaseTitle = relTitle,
                                releaseNotes = notes,
                                publishedAt = published
                            )
                        )
                    }
                }
            }
            // Prefer Firefox-signed/Android-specific .xpi builds first
            return assetsOut.sortedByDescending { asset ->
                val lower = asset.name.lowercase()
                var score = 0
                if ("signed" in lower) score += 4
                if ("android" in lower || "fennec" in lower || "fenix" in lower) score += 3
                if ("firefox" in lower || "gecko" in lower) score += 2
                if ("unsigned" in lower) score -= 5
                score
            }
        }

        private fun extractLocalizedString(value: Any?): String? {
            return when (value) {
                is String -> stripNull(value)
                is JSONObject -> {
                    stripNull(value.optString("en-US"))
                        ?: stripNull(value.optString("en"))
                        ?: value.keys().asSequence().firstOrNull()?.let { key ->
                            stripNull(value.optString(key))
                        }
                }
                else -> null
            }
        }

        private fun stripNull(raw: String?): String? {
            if (raw.isNullOrBlank() || raw.equals("null", ignoreCase = true)) return null
            return raw.trim()
        }

        private fun stripHtml(raw: String): String {
            return raw.replace(Regex("<[^>]*>"), " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}
