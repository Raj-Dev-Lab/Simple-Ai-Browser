package com.simpleaibrowser.sab.core.browser

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object UrlResolver {
    private val domainRegex = Regex(
        pattern = "^([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(:\\d+)?(/.*)?$"
    )
    private val ipv4Regex = Regex(
        pattern = "^(\\d{1,3}\\.){3}\\d{1,3}(:\\d+)?(/.*)?$"
    )

    fun resolve(
        rawInput: String,
        searchEngine: SearchEngine,
        httpsOnly: Boolean = true
    ): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty() || trimmed.equals(SAB_START_URL, ignoreCase = true)) {
            return SAB_START_URL
        }
        if (trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }
        if (trimmed.startsWith("http://", ignoreCase = true)) {
            return if (httpsOnly && !isLocalHost(trimmed)) {
                "https://" + trimmed.removePrefix("http://").removePrefix("HTTP://")
            } else {
                trimmed
            }
        }
        if (trimmed.startsWith("about:", ignoreCase = true)) {
            return trimmed
        }
        if (!trimmed.contains(" ") && (domainRegex.matches(trimmed) || ipv4Regex.matches(trimmed) || trimmed.startsWith("localhost"))) {
            val scheme = if (trimmed.startsWith("localhost") || ipv4Regex.matches(trimmed)) "http://" else "https://"
            return "$scheme$trimmed"
        }
        val encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.name())
        return searchEngine.queryTemplate.replace("%s", encoded)
    }

    fun extractHost(url: String): String {
        if (url == SAB_START_URL || url.isBlank()) return "Start Page"
        return try {
            val host = URI(url).host.orEmpty()
            host.removePrefix("www.").ifBlank { url }
        } catch (_: Exception) {
            url.removePrefix("https://").removePrefix("http://").substringBefore("/").removePrefix("www.")
        }
    }

    fun extractMonogram(titleOrUrl: String): String {
        val cleaned = extractHost(titleOrUrl).trim()
        return cleaned.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "W"
    }

    fun securityForUrl(url: String): SecurityState = when {
        url == SAB_START_URL || url.startsWith("about:") -> SecurityState.INTERNAL
        url.startsWith("https://", ignoreCase = true) -> SecurityState.SECURE
        else -> SecurityState.INSECURE
    }

    private fun isLocalHost(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://localhost") ||
            lower.startsWith("http://127.") ||
            lower.startsWith("http://10.") ||
            lower.startsWith("http://192.168.")
    }
}
