# Simple AI Browser (SAB) — Full System Architecture Blueprint

**Goal:** Replicate the clean, modular **Opera Mobile** browser architecture (UI shell, ergonomic bottom-bar navigation, Speed Dial, Tab Islands, AdBlock, and integrated AI panel), while **swapping the rendering engine from Chromium/Blink to pre-compiled Mozilla GeckoView 159+ (`geckoview-arm64-v8a`, non-omni, Android 10+)**.

---

## 1. High-Level Layered Architecture (Opera Pattern → SAB Implementation)

Opera Mobile's engineering strength comes from a **strict boundary between the Browser Shell (UI + Features) and the Rendering Engine**. SAB mirrors this exact separation:

```text
+-------------------------------------------------------------------------+
|  LAYER 1: PRESENTATION & OPERA-INSPIRED SHELL (:app / :feature-*)       |
|  - Splash Intro (Android 12+ SplashScreen + Animated Vector / Compose)  |
|  - Start Page (Speed Dial Grid, Theme Presets, Unified Omnibox)         |
|  - 5-Slot Bottom Nav Bar (Back, Search, SAB AI Orb, Tabs, Menu Sheet)   |
|  - Visual Tab Gallery & Tab Islands + Private Mode                      |
|  - SAB AI Contextual Bottom Sheet                                       |
+------------------------------------+------------------------------------+
                                     | Observes StateFlow / Dispatches Commands
                                     v
+-------------------------------------------------------------------------+
|  LAYER 2: ENGINE-AGNOSTIC BROWSER CORE (:core-browser)                  |
|  - BrowserEngine interface       - BrowserTabSession interface          |
|  - TabManager & TabIslandStore   - NavigationState / SecurityState      |
|  - PageContextSnapshot           - ContentBlockingStats                 |
|  (Zero imports from org.mozilla.geckoview or android.webkit!)           |
+------------------------------------+------------------------------------+
                                     | Implemented by
                                     v
+-------------------------------------------------------------------------+
|  LAYER 3: GECKOVIEW 159+ ENGINE ADAPTER (:engine-gecko)                 |
|  - org.mozilla.geckoview:geckoview-arm64-v8a:159+ (non-omni, API 29+)   |
|  - GeckoEngineAdapter            - GeckoTabSession                      |
|  - GeckoRuntime Singleton        - Delegate-to-Flow Mappers             |
|  - Built-in WebExtension ("sab-bridge") for DOM text & Reader/Dark CSS  |
+-------------------------------------------------------------------------+

+------------------------------------+------------------------------------+
|  LAYER 4: AI ORCHESTRATION         |  LAYER 5: LOCAL DATA & SECURITY    |
|  (:core-ai)                        |  (:core-data)                      |
|  - Context Builder (URL + DOM)     |  - Room DB (SpeedDial, History,    |
|  - Gemini API Client (Server/BYOK) |    Bookmarks, TabIslands, Stats)   |
|  - Prompt Templates (Summarize,    |  - Android Keystore / Encrypted    |
|    Translate, Explain, Compare)    |    Prefs for user secrets          |
+------------------------------------+------------------------------------+
```

---

## 2. Android Multi-Module Project Structure (Target Native Tree)

```text
android/
├── app/                              # Application entry, DI graph, Splash, MainActivity
├── core-browser/                     # Pure Kotlin engine-agnostic contracts & TabManager
├── core-ai/                          # AI Composer layer (context extraction + Gemini calls)
├── core-data/                        # Room database, DataStore preferences, Keystore
├── engine-gecko/                     # GeckoView 159+ arm64-v8a non-omni implementation
│   └── src/main/assets/extensions/
│       └── sab-bridge/               # Built-in WebExtension (DOM reader, dark mode, bridge)
├── feature-shell/                    # Opera-style Bottom Bar, Omnibox, Menu Bottom Sheet
├── feature-speeddial/                # Speed Dial grid, tile shapes, personalization
├── feature-tabs/                     # Tab Gallery, Tab Islands, Private Tabs switcher
└── feature-ai/                       # SAB AI Bottom Sheet & selected-text quick actions
```

---

## 3. Core Engine-Swap Contract (`:core-browser`)

Copying Opera's shell decoupling means **no UI screen ever imports `GeckoSession` directly**:

```kotlin
package com.rajdevlab.sab.core.browser

import kotlinx.coroutines.flow.StateFlow

data class TabUiState(
    val id: String,
    val islandId: String? = null,
    val title: String = "New Tab",
    val url: String = "sab://start",
    val isPrivate: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isSecure: Boolean = true,
    val blockedTrackersCount: Int = 0
)

interface BrowserTabSession {
    val state: StateFlow<TabUiState>
    fun loadUrl(urlOrQuery: String)
    fun goBack()
    fun goForward()
    fun reload()
    fun stopLoading()
    suspend fun extractPageText(maxChars: Int = 12_000): PageContextSnapshot
    fun setDesktopMode(enabled: Boolean)
    fun setReaderMode(enabled: Boolean)
    fun setForceDarkMode(enabled: Boolean)
    fun release()
}

interface BrowserEngine {
    val engineName: String // "GeckoView 159 (arm64-v8a)"
    fun createSession(tabId: String, isPrivate: Boolean): BrowserTabSession
    fun setAdBlockEnabled(enabled: Boolean)
    fun clearBrowsingData()
}
```

### Why This Design Wins
1. **Clean Opera UI Parity:** `:feature-shell`, `:feature-tabs`, and `:feature-speeddial` only observe `StateFlow<TabUiState>`.
2. **Zero Engine Lock-in:** Every GeckoView delegate (`NavigationDelegate`, `ProgressDelegate`, `ContentBlocking.Delegate`, `ScrollDelegate`) lives isolated inside `:engine-gecko`.
3. **AI-First Context Hooks:** Any AI feature in `:feature-ai` simply calls `activeTab.extractPageText()`—powered cleanly under the hood by our `sab-bridge` WebExtension on GeckoView.

---

## 4. Splash Screen & Brand Motion Specification

- **Android 10–11 Fallback & Android 12+ (`androidx.core:core-splashscreen`):**
  - Background: `#0B0D12` (Obsidian Matte)
  - Icon Layer: SAB Crimson/Silver geometric emblem (`288dp` adaptive icon safe-zone `108dp` circle)
  - **In-App Reveal Sequence (650ms total):**
    1. `0ms–220ms`: Orbital ring scales `0.86 → 1.0` with `CubicBezier(0.16, 1, 0.3, 1)` + opacity `0 → 1`.
    2. `160ms–420ms`: Inner S-aperture prism rotates `-14° → 0°` into lock.
    3. `320ms–650ms`: `"SAB"` / `"Simple AI Browser"` wordmark settles upward (`12dp → 0dp`) and cross-fades cleanly into the Start Page Speed Dial.
  - Supports `prefers-reduced-motion` / `AnimatorDurationScale == 0` by skipping directly to the settled Start Page.
