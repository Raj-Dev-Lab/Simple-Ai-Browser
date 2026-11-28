package com.simpleaibrowser.sab.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.simpleaibrowser.sab.core.browser.AddressBarPosition
import com.simpleaibrowser.sab.core.browser.CookieIsolationMode
import com.simpleaibrowser.sab.core.browser.DnsOverHttpsMode
import com.simpleaibrowser.sab.core.browser.DownloadManagerSettings
import com.simpleaibrowser.sab.core.browser.PrivacyConfig
import com.simpleaibrowser.sab.core.browser.SearchEngine
import com.simpleaibrowser.sab.core.browser.StorageLocationMode
import com.simpleaibrowser.sab.core.browser.ThemePreset
import com.simpleaibrowser.sab.core.browser.SecureRouting
import com.simpleaibrowser.sab.core.browser.TrackingProtectionLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// Self-healing store: if the preferences file is ever corrupted (e.g. the process was killed
// by a restart mid-write), ReplaceFileCorruptionHandler replaces the broken file with empty
// preferences instead of throwing on every subsequent read AND write. The flow-level .catch
// below stays as defense in depth.
private val Context.sabDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sab_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

data class BrowserSettings(
    val adBlockEnabled: Boolean = true,
    val trackingProtectionLevel: TrackingProtectionLevel = TrackingProtectionLevel.STANDARD,
    val doNotTrackEnabled: Boolean = true,
    val globalPrivacyControlEnabled: Boolean = true,
    val cookieIsolationMode: CookieIsolationMode = CookieIsolationMode.TOTAL_ISOLATE,
    val cookiePurgingEnabled: Boolean = true,
    val stripTrackingParams: Boolean = true,
    val bounceTrackingProtection: Boolean = true,
    val fingerprintingDefense: Boolean = true,
    val localNetworkProtection: Boolean = true,
    val safeBrowsingEnabled: Boolean = true,
    val dnsOverHttpsMode: DnsOverHttpsMode = DnsOverHttpsMode.OFF,
    val customDohUrl: String = "",
    val dohStrict: Boolean = false,
    val clearPrivateDataOnExit: Boolean = false,
    val httpsOnlyEnabled: Boolean = true,
    val forceDarkDefault: Boolean = false,
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val themePreset: ThemePreset = ThemePreset.OBSIDIAN_CRIMSON,
    val addressBarPosition: AddressBarPosition = AddressBarPosition.TOP,
    val restoreTabsOnStart: Boolean = true,
    val defaultTextZoom: Int = 100,
    val totalBlockedTrackers: Long = 0L,
    val showWallpaperOnStart: Boolean = true,
    val autoUpdateExtensions: Boolean = true,
    val lastExtensionUpdateCheckAt: Long = 0L,
    val incognitoAutoShieldEnabled: Boolean = true,
    val routingMode: SecureRouting.RoutingMode = SecureRouting.RoutingMode.OFF,
    val routingSocksHost: String = SecureRouting.DEFAULT_TOR_HOST,
    val routingSocksPort: Int = SecureRouting.DEFAULT_TOR_PORT,
    val downloadSettings: DownloadManagerSettings = DownloadManagerSettings()
) {
    fun toPrivacyConfig(): PrivacyConfig = PrivacyConfig(
        trackingProtectionLevel = if (!adBlockEnabled) TrackingProtectionLevel.OFF else trackingProtectionLevel,
        doNotTrackEnabled = doNotTrackEnabled,
        globalPrivacyControlEnabled = globalPrivacyControlEnabled,
        cookieIsolationMode = cookieIsolationMode,
        cookiePurgingEnabled = cookiePurgingEnabled,
        stripTrackingParams = stripTrackingParams,
        bounceTrackingProtection = bounceTrackingProtection,
        fingerprintingDefense = fingerprintingDefense,
        localNetworkProtection = localNetworkProtection,
        safeBrowsingEnabled = safeBrowsingEnabled,
        httpsOnlyEnabled = httpsOnlyEnabled,
        dnsOverHttpsMode = dnsOverHttpsMode,
        customDohUrl = customDohUrl,
        dohStrict = dohStrict
    )
}

class BrowserPreferences(private val context: Context) {
    private object Keys {
        val AD_BLOCK = booleanPreferencesKey("ad_block_enabled")
        val HTTPS_ONLY = booleanPreferencesKey("https_only_enabled")
        val FORCE_DARK = booleanPreferencesKey("force_dark_default")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine_id")
        val THEME_PRESET = stringPreferencesKey("theme_preset_id")
        val ADDRESS_BAR_POSITION = stringPreferencesKey("address_bar_position")
        val RESTORE_TABS = booleanPreferencesKey("restore_tabs_on_start")
        val DEFAULT_TEXT_ZOOM = intPreferencesKey("default_text_zoom")
        val TOTAL_BLOCKED = longPreferencesKey("total_blocked_trackers")
        val SHOW_WALLPAPER = booleanPreferencesKey("show_wallpaper_on_start")
        val AUTO_UPDATE_EXTENSIONS = booleanPreferencesKey("auto_update_extensions")
        val LAST_EXTENSION_CHECK = longPreferencesKey("last_extension_update_check_at")

        // Download Manager Keys
        val DL_ASK_BEFORE = booleanPreferencesKey("dl_ask_before")
        val DL_THREADS = intPreferencesKey("dl_default_threads")
        val DL_MAX_CONCURRENT = intPreferencesKey("dl_max_concurrent")
        val DL_WIFI_ONLY = booleanPreferencesKey("dl_wifi_only")
        val DL_SPEED_LIMIT_KBPS = intPreferencesKey("dl_speed_limit_kbps")
        val DL_AUTO_RESUME = booleanPreferencesKey("dl_auto_resume")
        val DL_STORAGE_MODE = stringPreferencesKey("dl_storage_mode")
        val DL_CUSTOM_TREE_URI = stringPreferencesKey("dl_custom_tree_uri")
        val DL_CUSTOM_TREE_LABEL = stringPreferencesKey("dl_custom_tree_label")
        val DL_AUTO_ORGANIZE = booleanPreferencesKey("dl_auto_organize_by_type")
        val DL_NOTIFY_COMPLETE = booleanPreferencesKey("dl_notify_complete")
        val DL_TORRENT_SEQUENTIAL = booleanPreferencesKey("dl_torrent_sequential")
        val DL_TORRENT_SEED = booleanPreferencesKey("dl_torrent_seed")
        val DL_TORRENT_MAX_CONN = intPreferencesKey("dl_torrent_max_conn")
        val DL_TORRENT_DL_LIMIT = intPreferencesKey("dl_torrent_dl_limit_kbps")
        val DL_TORRENT_UL_LIMIT = intPreferencesKey("dl_torrent_ul_limit_kbps")
        val DL_TORRENT_DHT = booleanPreferencesKey("dl_torrent_enable_dht")
        val DL_USER_AGENT = stringPreferencesKey("dl_custom_user_agent")

        // Privacy Suite Keys
        val PRIV_TRACKING_LEVEL = stringPreferencesKey("priv_tracking_level")
        val PRIV_DNT = booleanPreferencesKey("priv_do_not_track")
        val PRIV_GPC = booleanPreferencesKey("priv_global_privacy_control")
        val PRIV_COOKIE_MODE = stringPreferencesKey("priv_cookie_isolation_mode")
        val PRIV_COOKIE_PURGE = booleanPreferencesKey("priv_cookie_purging")
        val PRIV_STRIP_PARAMS = booleanPreferencesKey("priv_strip_tracking_params")
        val PRIV_BOUNCE = booleanPreferencesKey("priv_bounce_tracking")
        val PRIV_FINGERPRINT = booleanPreferencesKey("priv_fingerprinting_defense")
        val PRIV_LNA = booleanPreferencesKey("priv_local_network_protection")
        val PRIV_SAFE_BROWSING = booleanPreferencesKey("priv_safe_browsing")
        val PRIV_DOH_MODE = stringPreferencesKey("priv_doh_mode")
        val PRIV_DOH_CUSTOM_URL = stringPreferencesKey("priv_doh_custom_url")
        val PRIV_DOH_STRICT = booleanPreferencesKey("priv_doh_strict")
        val PRIV_CLEAR_ON_EXIT = booleanPreferencesKey("priv_clear_on_exit")

        // Incognito Auto-Shield + Secure Routing
        val SHIELD_AUTO_ENABLED = booleanPreferencesKey("incognito_auto_shield_enabled")
        val ROUTING_MODE = stringPreferencesKey("routing_mode")
        val ROUTING_SOCKS_HOST = stringPreferencesKey("routing_socks_host")
        val ROUTING_SOCKS_PORT = intPreferencesKey("routing_socks_port")
    }

    val settingsFlow: Flow<BrowserSettings> = context.sabDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val adBlock = prefs[Keys.AD_BLOCK] ?: true
            val trackingLevel = prefs[Keys.PRIV_TRACKING_LEVEL]?.let {
                TrackingProtectionLevel.fromId(it)
            } ?: if (adBlock) TrackingProtectionLevel.STANDARD else TrackingProtectionLevel.OFF

            BrowserSettings(
                adBlockEnabled = trackingLevel != TrackingProtectionLevel.OFF && adBlock,
                trackingProtectionLevel = trackingLevel,
                doNotTrackEnabled = prefs[Keys.PRIV_DNT] ?: true,
                globalPrivacyControlEnabled = prefs[Keys.PRIV_GPC] ?: true,
                cookieIsolationMode = CookieIsolationMode.fromId(
                    prefs[Keys.PRIV_COOKIE_MODE] ?: CookieIsolationMode.TOTAL_ISOLATE.id
                ),
                cookiePurgingEnabled = prefs[Keys.PRIV_COOKIE_PURGE] ?: true,
                stripTrackingParams = prefs[Keys.PRIV_STRIP_PARAMS] ?: true,
                bounceTrackingProtection = prefs[Keys.PRIV_BOUNCE] ?: true,
                fingerprintingDefense = prefs[Keys.PRIV_FINGERPRINT] ?: true,
                localNetworkProtection = prefs[Keys.PRIV_LNA] ?: true,
                safeBrowsingEnabled = prefs[Keys.PRIV_SAFE_BROWSING] ?: true,
                dnsOverHttpsMode = DnsOverHttpsMode.fromId(
                    prefs[Keys.PRIV_DOH_MODE] ?: DnsOverHttpsMode.OFF.id
                ),
                customDohUrl = prefs[Keys.PRIV_DOH_CUSTOM_URL].orEmpty(),
                dohStrict = prefs[Keys.PRIV_DOH_STRICT] ?: false,
                clearPrivateDataOnExit = prefs[Keys.PRIV_CLEAR_ON_EXIT] ?: false,
                incognitoAutoShieldEnabled = prefs[Keys.SHIELD_AUTO_ENABLED] ?: true,
                routingMode = SecureRouting.RoutingMode.fromId(prefs[Keys.ROUTING_MODE] ?: SecureRouting.RoutingMode.OFF.id),
                routingSocksHost = prefs[Keys.ROUTING_SOCKS_HOST] ?: SecureRouting.DEFAULT_TOR_HOST,
                routingSocksPort = (prefs[Keys.ROUTING_SOCKS_PORT] ?: SecureRouting.DEFAULT_TOR_PORT).coerceIn(1, 65535),
                httpsOnlyEnabled = prefs[Keys.HTTPS_ONLY] ?: true,
                forceDarkDefault = prefs[Keys.FORCE_DARK] ?: false,
                searchEngine = SearchEngine.fromId(prefs[Keys.SEARCH_ENGINE] ?: SearchEngine.DUCKDUCKGO.id),
                themePreset = ThemePreset.fromId(prefs[Keys.THEME_PRESET] ?: ThemePreset.OBSIDIAN_CRIMSON.id),
                addressBarPosition = AddressBarPosition.fromId(
                    prefs[Keys.ADDRESS_BAR_POSITION] ?: AddressBarPosition.TOP.id
                ),
                restoreTabsOnStart = prefs[Keys.RESTORE_TABS] ?: true,
                defaultTextZoom = (prefs[Keys.DEFAULT_TEXT_ZOOM] ?: 100).coerceIn(75, 160),
                totalBlockedTrackers = prefs[Keys.TOTAL_BLOCKED] ?: 0L,
                showWallpaperOnStart = prefs[Keys.SHOW_WALLPAPER] ?: true,
                autoUpdateExtensions = prefs[Keys.AUTO_UPDATE_EXTENSIONS] ?: true,
                lastExtensionUpdateCheckAt = prefs[Keys.LAST_EXTENSION_CHECK] ?: 0L,
                downloadSettings = DownloadManagerSettings(
                    askBeforeDownloading = prefs[Keys.DL_ASK_BEFORE] ?: false,
                    defaultConnectionsPerDownload = (prefs[Keys.DL_THREADS] ?: 4).coerceIn(1, 16),
                    maxConcurrentDownloads = (prefs[Keys.DL_MAX_CONCURRENT] ?: 3).coerceIn(1, 6),
                    wifiOnlyDownloads = prefs[Keys.DL_WIFI_ONLY] ?: false,
                    speedLimitKbps = (prefs[Keys.DL_SPEED_LIMIT_KBPS] ?: 0).coerceAtLeast(0),
                    autoResumeOnReconnect = prefs[Keys.DL_AUTO_RESUME] ?: true,
                    storageLocationMode = StorageLocationMode.fromId(
                        prefs[Keys.DL_STORAGE_MODE] ?: StorageLocationMode.PUBLIC_DOWNLOADS.id
                    ),
                    customTreeUri = prefs[Keys.DL_CUSTOM_TREE_URI]?.takeIf { it.isNotBlank() },
                    customTreeLabel = prefs[Keys.DL_CUSTOM_TREE_LABEL]?.takeIf { it.isNotBlank() },
                    autoOrganizeByType = prefs[Keys.DL_AUTO_ORGANIZE] ?: false,
                    notifyOnComplete = prefs[Keys.DL_NOTIFY_COMPLETE] ?: true,
                    torrentSequentialDefault = prefs[Keys.DL_TORRENT_SEQUENTIAL] ?: false,
                    torrentSeedAfterComplete = prefs[Keys.DL_TORRENT_SEED] ?: false,
                    torrentMaxConnections = (prefs[Keys.DL_TORRENT_MAX_CONN] ?: 200).coerceIn(20, 500),
                    torrentDownloadLimitKbps = (prefs[Keys.DL_TORRENT_DL_LIMIT] ?: 0).coerceAtLeast(0),
                    torrentUploadLimitKbps = (prefs[Keys.DL_TORRENT_UL_LIMIT] ?: 128).coerceAtLeast(0),
                    torrentEnableDht = prefs[Keys.DL_TORRENT_DHT] ?: true,
                    customUserAgent = prefs[Keys.DL_USER_AGENT].orEmpty()
                )
            )
        }

    suspend fun setAdBlockEnabled(enabled: Boolean) {
        context.sabDataStore.edit { prefs ->
            prefs[Keys.AD_BLOCK] = enabled
            if (!enabled) {
                prefs[Keys.PRIV_TRACKING_LEVEL] = TrackingProtectionLevel.OFF.id
            } else if (prefs[Keys.PRIV_TRACKING_LEVEL] == TrackingProtectionLevel.OFF.id ||
                prefs[Keys.PRIV_TRACKING_LEVEL] == null
            ) {
                prefs[Keys.PRIV_TRACKING_LEVEL] = TrackingProtectionLevel.STANDARD.id
            }
        }
    }

    suspend fun setTrackingProtectionLevel(level: TrackingProtectionLevel) {
        context.sabDataStore.edit { prefs ->
            prefs[Keys.PRIV_TRACKING_LEVEL] = level.id
            prefs[Keys.AD_BLOCK] = level != TrackingProtectionLevel.OFF
        }
    }

    suspend fun setDoNotTrackEnabled(enabled: Boolean) {
        context.sabDataStore.edit { it[Keys.PRIV_DNT] = enabled }
    }

    suspend fun setGlobalPrivacyControlEnabled(enabled: Boolean) {
        context.sabDataStore.edit { it[Keys.PRIV_GPC] = enabled }
    }

    suspend fun setCookieIsolationMode(mode: CookieIsolationMode) {
        context.sabDataStore.edit { it[Keys.PRIV_COOKIE_MODE] = mode.id }
    }

    suspend fun setCookiePurgingEnabled(enabled: Boolean) {
        context.sabDataStore.edit { it[Keys.PRIV_COOKIE_PURGE] = enabled }
    }

    suspend fun setStripTrackingParams(enabled: Boolean) {
        context.sabDataStore.edit { it[Keys.PRIV_STRIP_PARAMS] = enabled }
    }

    suspend fun setBounceTrackingProtection(enabled: Boolean) {
        context.sabDataStore.edit { it[Keys.PRIV_BOUNCE] = enabled }
    }

    suspend fun setFingerprintingDefense(enabled: Boolean) {
        context.sabDataStore.edit { it[Keys.PRIV_FINGERPRINT] = enabled }
    }

    suspend fun setLocalNetworkProtection(enabled: Boolean) {
        context.sabDataStore.edit { it[Keys.PRIV_LNA] = enabled }
    }

    suspend fun setSafeBrowsingEnabled(enabled: Boolean) {
        context.sabDataStore.edit { it[Keys.PRIV_SAFE_BROWSING] = enabled }
    }

    suspend fun setDnsOverHttpsConfig(mode: DnsOverHttpsMode, customUrl: String, strict: Boolean) {
        context.sabDataStore.edit { prefs ->
            prefs[Keys.PRIV_DOH_MODE] = mode.id
            prefs[Keys.PRIV_DOH_CUSTOM_URL] = customUrl.trim()
            prefs[Keys.PRIV_DOH_STRICT] = strict
        }
    }

    suspend fun setClearPrivateDataOnExit(enabled: Boolean) {
        context.sabDataStore.edit { it[Keys.PRIV_CLEAR_ON_EXIT] = enabled }
    }

    suspend fun setHttpsOnlyEnabled(enabled: Boolean) {
        context.sabDataStore.edit { it[Keys.HTTPS_ONLY] = enabled }
    }

    suspend fun setIncognitoAutoShieldEnabled(enabled: Boolean) {
        context.sabDataStore.edit { it[Keys.SHIELD_AUTO_ENABLED] = enabled }
    }

    suspend fun setRoutingConfig(mode: SecureRouting.RoutingMode, host: String, port: Int) {
        context.sabDataStore.edit {
            it[Keys.ROUTING_MODE] = mode.id
            it[Keys.ROUTING_SOCKS_HOST] = host
            it[Keys.ROUTING_SOCKS_PORT] = port
        }
    }

    suspend fun setForceDarkDefault(enabled: Boolean) {
        context.sabDataStore.edit { it[Keys.FORCE_DARK] = enabled }
    }

    suspend fun setSearchEngine(engine: SearchEngine) {
        context.sabDataStore.edit { it[Keys.SEARCH_ENGINE] = engine.id }
    }

    suspend fun setThemePreset(preset: ThemePreset) {
        context.sabDataStore.edit { it[Keys.THEME_PRESET] = preset.id }
    }

    suspend fun setAddressBarPosition(position: AddressBarPosition) {
        context.sabDataStore.edit { it[Keys.ADDRESS_BAR_POSITION] = position.id }
    }

    suspend fun setRestoreTabsOnStart(enabled: Boolean) {
        context.sabDataStore.edit { it[Keys.RESTORE_TABS] = enabled }
    }

    suspend fun setDefaultTextZoom(percent: Int) {
        context.sabDataStore.edit { it[Keys.DEFAULT_TEXT_ZOOM] = percent.coerceIn(75, 160) }
    }

    suspend fun setShowWallpaperOnStart(enabled: Boolean) {
        context.sabDataStore.edit { it[Keys.SHOW_WALLPAPER] = enabled }
    }

    suspend fun setAutoUpdateExtensions(enabled: Boolean) {
        context.sabDataStore.edit { it[Keys.AUTO_UPDATE_EXTENSIONS] = enabled }
    }

    suspend fun setLastExtensionUpdateCheckAt(timestamp: Long) {
        context.sabDataStore.edit { it[Keys.LAST_EXTENSION_CHECK] = timestamp }
    }

    suspend fun updateDownloadSettings(transform: (DownloadManagerSettings) -> DownloadManagerSettings) {
        context.sabDataStore.edit { prefs ->
            val current = DownloadManagerSettings(
                askBeforeDownloading = prefs[Keys.DL_ASK_BEFORE] ?: false,
                defaultConnectionsPerDownload = (prefs[Keys.DL_THREADS] ?: 4).coerceIn(1, 16),
                maxConcurrentDownloads = (prefs[Keys.DL_MAX_CONCURRENT] ?: 3).coerceIn(1, 6),
                wifiOnlyDownloads = prefs[Keys.DL_WIFI_ONLY] ?: false,
                speedLimitKbps = (prefs[Keys.DL_SPEED_LIMIT_KBPS] ?: 0).coerceAtLeast(0),
                autoResumeOnReconnect = prefs[Keys.DL_AUTO_RESUME] ?: true,
                storageLocationMode = StorageLocationMode.fromId(
                    prefs[Keys.DL_STORAGE_MODE] ?: StorageLocationMode.PUBLIC_DOWNLOADS.id
                ),
                customTreeUri = prefs[Keys.DL_CUSTOM_TREE_URI]?.takeIf { it.isNotBlank() },
                customTreeLabel = prefs[Keys.DL_CUSTOM_TREE_LABEL]?.takeIf { it.isNotBlank() },
                autoOrganizeByType = prefs[Keys.DL_AUTO_ORGANIZE] ?: false,
                notifyOnComplete = prefs[Keys.DL_NOTIFY_COMPLETE] ?: true,
                torrentSequentialDefault = prefs[Keys.DL_TORRENT_SEQUENTIAL] ?: false,
                torrentSeedAfterComplete = prefs[Keys.DL_TORRENT_SEED] ?: false,
                torrentMaxConnections = (prefs[Keys.DL_TORRENT_MAX_CONN] ?: 200).coerceIn(20, 500),
                torrentDownloadLimitKbps = (prefs[Keys.DL_TORRENT_DL_LIMIT] ?: 0).coerceAtLeast(0),
                torrentUploadLimitKbps = (prefs[Keys.DL_TORRENT_UL_LIMIT] ?: 128).coerceAtLeast(0),
                torrentEnableDht = prefs[Keys.DL_TORRENT_DHT] ?: true,
                customUserAgent = prefs[Keys.DL_USER_AGENT].orEmpty()
            )
            val next = transform(current)
            prefs[Keys.DL_ASK_BEFORE] = next.askBeforeDownloading
            prefs[Keys.DL_THREADS] = next.defaultConnectionsPerDownload.coerceIn(1, 16)
            prefs[Keys.DL_MAX_CONCURRENT] = next.maxConcurrentDownloads.coerceIn(1, 6)
            prefs[Keys.DL_WIFI_ONLY] = next.wifiOnlyDownloads
            prefs[Keys.DL_SPEED_LIMIT_KBPS] = next.speedLimitKbps.coerceAtLeast(0)
            prefs[Keys.DL_AUTO_RESUME] = next.autoResumeOnReconnect
            prefs[Keys.DL_STORAGE_MODE] = next.storageLocationMode.id
            prefs[Keys.DL_CUSTOM_TREE_URI] = next.customTreeUri.orEmpty()
            prefs[Keys.DL_CUSTOM_TREE_LABEL] = next.customTreeLabel.orEmpty()
            prefs[Keys.DL_AUTO_ORGANIZE] = next.autoOrganizeByType
            prefs[Keys.DL_NOTIFY_COMPLETE] = next.notifyOnComplete
            prefs[Keys.DL_TORRENT_SEQUENTIAL] = next.torrentSequentialDefault
            prefs[Keys.DL_TORRENT_SEED] = next.torrentSeedAfterComplete
            prefs[Keys.DL_TORRENT_MAX_CONN] = next.torrentMaxConnections.coerceIn(20, 500)
            prefs[Keys.DL_TORRENT_DL_LIMIT] = next.torrentDownloadLimitKbps.coerceAtLeast(0)
            prefs[Keys.DL_TORRENT_UL_LIMIT] = next.torrentUploadLimitKbps.coerceAtLeast(0)
            prefs[Keys.DL_TORRENT_DHT] = next.torrentEnableDht
            prefs[Keys.DL_USER_AGENT] = next.customUserAgent
        }
    }

    suspend fun incrementBlockedTrackers(delta: Long = 1L) {
        if (delta <= 0L) return
        context.sabDataStore.edit { prefs ->
            val current = prefs[Keys.TOTAL_BLOCKED] ?: 0L
            prefs[Keys.TOTAL_BLOCKED] = current + delta
        }
    }

    suspend fun resetBlockedCounter() {
        context.sabDataStore.edit { it[Keys.TOTAL_BLOCKED] = 0L }
    }
}
