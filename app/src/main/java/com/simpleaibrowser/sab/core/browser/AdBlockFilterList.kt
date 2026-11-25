package com.simpleaibrowser.sab.core.browser

/**
 * Curated high-signal advertising & tracker host blocklist used for
 * telemetry classification and fallback engine request interception.
 * On GeckoView, native Enhanced Tracking Protection (ETP) handles full
 * Disconnect/Mozilla list enforcement at the Gecko network layer.
 */
object AdBlockFilterList {
    private val blockedHosts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "googletagmanager.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "connect.facebook.net",
        "pixel.facebook.com",
        "an.facebook.com",
        "ads.twitter.com",
        "analytics.twitter.com",
        "ads.linkedin.com",
        "snap.licdn.com",
        "adnxs.com",
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "casalemedia.com",
        "scorecardresearch.com",
        "quantserve.com",
        "hotjar.com",
        "mixpanel.com",
        "amplitude.com",
        "segment.io",
        "clarity.ms",
        "amazon-adsystem.com",
        "moatads.com",
        "chartbeat.com",
        "newrelic.com",
        "bam.nr-data.net",
        "branch.io",
        "appsflyer.com",
        "adjust.com",
        "adcolony.com",
        "unityads.unity3d.com"
    )

    fun isBlockedHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val normalized = host.lowercase().trim()
        if (normalized in blockedHosts) return true
        return blockedHosts.any { blocked -> normalized.endsWith(".$blocked") }
    }
}
