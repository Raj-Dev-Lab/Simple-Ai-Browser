package com.simpleaibrowser.sab.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
