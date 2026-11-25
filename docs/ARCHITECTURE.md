# Simple AI Browser (SAB) — System Architecture & Opera Parity Analysis

## 1. Architectural Strategy: Opera Mobile Shell × Mozilla GeckoView Engine

Opera Mobile's longevity and responsiveness stem from a **strict separation between the Browser Shell (UI, Tabs, Speed Dial, Privacy Controls) and the Rendering Engine**. SAB replicates this exact production pattern while swapping Chromium/Blink for pre-compiled **Mozilla GeckoView 161+ (`geckoview-arm64-v8a`, non-omni)**.

### Layer Breakdown

1. **Presentation Layer (`com.simpleaibrowser.sab.ui`)**
   - Pure Jetpack Compose + Material 3 shell (`SabBrowserApp`, `SpeedDialStartPage`, `TabGalleryScreen`, `OperaMenuBottomSheet`, `PrivacyShieldSheet`, `LibraryScreen`, `SettingsScreen`).
   - Observes reactive `StateFlow` state from `BrowserViewModel`.
   - **Zero direct imports** of `org.mozilla.geckoview.*` or `android.webkit.*`.
   - `EngineViewport` attaches and detaches the active `BrowserTabSession` cleanly through `BrowserEngine.createRenderView(context)`.

2. **Engine-Agnostic Browser Core (`com.simpleaibrowser.sab.core.browser`)**
   - Defines `BrowserEngine`, `BrowserTabSession`, `TabUiState`, `TabIsland`, `SecurityState`, `SearchEngine`, `ThemePreset`, `EngineInfo`, and `PageContextSnapshot`.
   - Includes `UrlResolver` (smart omnibox URL/search disambiguation + HTTPS-Only upgrading) and `AdBlockFilterList`.

3. **Engine Layer (`com.simpleaibrowser.sab.engine`)**
   - `GeckoEngineAdapter` & `GeckoTabSession`: Singleton `GeckoRuntime` configured with Enhanced Tracking Protection (`AD | ANALYTIC | SOCIAL | CRYPTOMINING | FINGERPRINTING`), Safe Browsing, `ACCEPT_NON_TRACKERS` cookies, and built-in `sab-bridge` WebExtension.
   - `BrowserEngineFactory`: Always initializes `GeckoEngineAdapter` on `arm64-v8a` Android 10+ devices. Includes `CompatWebViewEngine` purely as a non-ARM64 host guard so x86_64 dev containers remain functional without crashing.

4. **Local Persistence Layer (`com.simpleaibrowser.sab.data`)**
   - **Room (`SabDatabase`)**: `speed_dials`, `bookmarks`, and `history` tables exposed via reactive Kotlin `Flow`.
   - **Jetpack DataStore (`BrowserPreferences`)**: AdBlock toggle, HTTPS-Only mode, Force Dark default, default Search Engine, Theme Preset, Start Page wallpaper toggle, and cumulative blocked-tracker telemetry.

## 2. Opera Mobile UX Feature Parity Matrix

| Opera Mobile Pattern | SAB Implementation |
|---|---|
| 5-Slot Bottom Thumb-Zone Navigation | `OperaBottomBar`: Back, Home, glowing Center Fast Action Search Orb (long-press = New Tab), Tab Counter Badge, Opera Menu |
| Start Page & Speed Dial | `SpeedDialStartPage`: Atmospheric obsidian wallpaper, live Privacy Shield pill, 4-column customizable Speed Dial grid, Quick Actions |
| Tab Gallery + Tab Islands | `TabGalleryScreen`: Regular / Islands / Private switcher, Grid & List views, live tab search, automatic child-tab grouping into colored Islands, manual Island creation/collapse/close, Recently Closed undo stack |
| Built-in Ad & Tracker Blocker | GeckoView Enhanced Tracking Protection + Safe Browsing + live per-tab & lifetime counter in `PrivacyShieldSheet` |
| Force Dark & Reader Focus | Native CSS injection via built-in `sab-bridge` GeckoView WebExtension |
| Personalization | 4 Material 3 Theme Presets (`Obsidian Crimson`, `Midnight Cobalt`, `Aurora Velvet`, `Daylight Studio`) + custom bundled typography |

## 3. Future-Ready AI Extension Point (Phase 3)
`BrowserTabSession.extractPageSnapshot()` and `sab-bridge/content.js` (`EXTRACT_SNAPSHOT`) are already wired into the engine boundary so future AI capabilities (page summarization, contextual Q&A, translation, Tab Island comparison) plug in as an independent module without touching any GeckoView rendering code.
