# Simple AI Browser (SAB) — System Architecture & Opera Parity Analysis

## 1. Architectural Strategy: Opera Mobile Shell × Mozilla GeckoView Engine

Opera Mobile's longevity and responsiveness stem from a **strict separation between the Browser Shell (UI, Tabs, Speed Dial, Privacy Controls) and the Rendering Engine**. SAB replicates this exact production pattern while swapping Chromium/Blink for pre-compiled **Mozilla GeckoView 161+ (`non-omni`, `arm64-v8a` + `x86_64`)**.

### Layer Breakdown

1. **Presentation Layer (`com.simpleaibrowser.sab.ui`)**
   - Pure Jetpack Compose + Material 3 shell (`SabBrowserApp`, `SpeedDialStartPage`, `TabGalleryScreen`, `OperaMenuBottomSheet`, `LinkContextBottomSheet`, `WebPromptDialog`, `PrivacyShieldSheet`, `LibraryScreen`, `SettingsScreen`).
   - Supports dynamic **Top or Bottom Address Bar** layouts, horizontal address bar tab-swiping, and immersive **Fullscreen Video** system bar hiding.
   - Observes reactive `StateFlow` state from `BrowserViewModel`.
   - **Zero direct imports** of `org.mozilla.geckoview.*` or `android.webkit.*`.
   - `EngineViewport` attaches and detaches the active `BrowserTabSession` cleanly through `BrowserEngine.createRenderView(context)`.

2. **Engine-Agnostic Browser Core (`com.simpleaibrowser.sab.core.browser`)**
   - Defines `BrowserEngine`, `BrowserTabSession`, `SessionCallbacks`, `TabUiState`, `TabIsland`, `AddressBarPosition`, `FindInPageResult`, `DownloadRequest`, `ContextMenuTarget`, `WebPromptRequest`, `SecurityState`, `SearchEngine`, `ThemePreset`, `EngineInfo`, and `PageContextSnapshot`.
   - Includes `UrlResolver` (smart omnibox URL/search disambiguation, HTTPS-Only upgrading, favicon resolution, and download filename inference) and `AdBlockFilterList`.

3. **Engine Layer (`com.simpleaibrowser.sab.engine`)**
   - `GeckoEngineAdapter` & `GeckoTabSession`: Singleton `GeckoRuntime` configured with Enhanced Tracking Protection (`AD | ANALYTIC | SOCIAL | CRYPTOMINING | FINGERPRINTING`), Safe Browsing, `ACCEPT_NON_TRACKERS` cookies, full `ContentDelegate` (downloads, context menu, fullscreen), `PromptDelegate`, `PermissionDelegate`, `SessionFinder`, and built-in `sab-bridge` WebExtension.
   - `BrowserEngineFactory`: Initializes `GeckoEngineAdapter` natively across `arm64-v8a` Android 10+ devices and `x86_64` Android emulators, with `CompatWebViewEngine` retained only as a defensive fallback guard.

4. **Local Persistence Layer (`com.simpleaibrowser.sab.data`)**
   - **Room (`SabDatabase`, schema v2 with `MIGRATION_1_2`)**: `speed_dials`, `bookmarks`, `history`, `downloads`, `saved_tabs`, and `saved_islands` tables exposed via reactive Kotlin `Flow`.
   - **Jetpack DataStore (`BrowserPreferences`)**: AdBlock toggle, HTTPS-Only mode, Force Dark default, default Search Engine, Theme Preset, Address Bar Position (`top`/`bottom`), Restore Tabs on Startup, Default Page Text Zoom, Start Page wallpaper toggle, and cumulative blocked-tracker telemetry.

## 2. Opera Mobile UX Feature Parity Matrix

| Opera Mobile Pattern | SAB Implementation |
|---|---|
| 5-Slot Bottom Thumb-Zone Navigation | `OperaBottomBar`: Back, Home, glowing Center Fast Action Search Orb (long-press = New Tab), Tab Counter Badge, Opera Menu |
| Top / Bottom One-Handed Address Bar | Configurable `AddressBarPosition.TOP` / `BOTTOM` + horizontal drag on address pill to switch tabs |
| Start Page & Speed Dial | `SpeedDialStartPage`: Atmospheric obsidian wallpaper, live Privacy Shield pill, 4-column Speed Dial with Coil favicons, Quick Actions (Private, Bookmarks, History, Downloads, Settings) |
| Tab Gallery + Tab Islands + Restore | `TabGalleryScreen`: Regular / Islands / Private switcher, Grid & List views, live tab search, automatic child-tab grouping, Island renaming, long-press tab-to-island assignment, Recently Closed undo, automatic Room session restore |
| Built-in Ad & Tracker Blocker | GeckoView Enhanced Tracking Protection + Safe Browsing + live per-tab & lifetime counter in `PrivacyShieldSheet` |
| Force Dark, Reader & Text Zoom | Native CSS & typography injection via built-in `sab-bridge` GeckoView WebExtension (`75%–160%` live text zoom) |
| Downloads & Long-Press Context Menus | Android `DownloadManager` (zero storage permissions on API 29+) + `Downloads` Library tab + `LinkContextBottomSheet` |
| Fullscreen Media & Web Prompts | Native `WindowInsetsControllerCompat` immersive fullscreen + Material 3 `WebPromptDialog` for JS alerts/confirms/prompts & site permissions |

## 3. Future-Ready AI Extension Point (Phase 3)
`BrowserTabSession.extractPageSnapshot()` and `sab-bridge/content.js` (`EXTRACT_SNAPSHOT`) are already wired into the engine boundary so future AI capabilities (page summarization, contextual Q&A, translation, Tab Island comparison) plug in as an independent module without touching any GeckoView rendering code.
