package com.simpleaibrowser.sab.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.simpleaibrowser.sab.core.browser.AddressBarPosition
import com.simpleaibrowser.sab.core.browser.SearchEngine
import com.simpleaibrowser.sab.core.browser.ThemePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sabDataStore: DataStore<Preferences> by preferencesDataStore(name = "sab_preferences")

data class BrowserSettings(
    val adBlockEnabled: Boolean = true,
    val httpsOnlyEnabled: Boolean = true,
    val forceDarkDefault: Boolean = false,
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val themePreset: ThemePreset = ThemePreset.OBSIDIAN_CRIMSON,
    val addressBarPosition: AddressBarPosition = AddressBarPosition.TOP,
    val restoreTabsOnStart: Boolean = true,
    val defaultTextZoom: Int = 100,
    val totalBlockedTrackers: Long = 0L,
    val showWallpaperOnStart: Boolean = true
)

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
    }

    val settingsFlow: Flow<BrowserSettings> = context.sabDataStore.data.map { prefs ->
        BrowserSettings(
            adBlockEnabled = prefs[Keys.AD_BLOCK] ?: true,
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
            showWallpaperOnStart = prefs[Keys.SHOW_WALLPAPER] ?: true
        )
    }

    suspend fun setAdBlockEnabled(enabled: Boolean) {
        context.sabDataStore.edit { it[Keys.AD_BLOCK] = enabled }
    }

    suspend fun setHttpsOnlyEnabled(enabled: Boolean) {
        context.sabDataStore.edit { it[Keys.HTTPS_ONLY] = enabled }
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

    suspend fun incrementBlockedTrackers(delta: Long = 1L) {
        context.sabDataStore.edit { prefs ->
            val current = prefs[Keys.TOTAL_BLOCKED] ?: 0L
            prefs[Keys.TOTAL_BLOCKED] = current + delta
        }
    }

    suspend fun resetBlockedCounter() {
        context.sabDataStore.edit { it[Keys.TOTAL_BLOCKED] = 0L }
    }
}
