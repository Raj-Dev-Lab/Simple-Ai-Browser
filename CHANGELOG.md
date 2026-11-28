# Changelog

All notable changes to **Simple AI Browser (SAB)** are documented in this file.  
This project adheres to [Semantic Versioning](https://semver.org/) and [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.4.1] - 2026-11-26

### Fixed
- **Splash animation no longer janks**: every animated property (logo scale/rotation/alpha, wordmark fade/offset, aura radius) now runs in the GPU draw phase (`graphicsLayer` / draw lambdas) instead of recomposing the whole overlay on every frame; the full-screen aura gradient is rasterized once per size at its maximum radius and then GPU-scaled instead of allocating a new radial-gradient shader ~60 times per second; the orbit-ring sweep gradient and the engine-status label are cached instead of being rebuilt per frame. The brand logo asset is now 512×512 (identical on every device — it never renders above 496 px) and the start-page wallpaper decodes off the main thread via Coil while it is still hidden behind the opaque splash, removing the two main-thread bitmap decodes from the splash window.
- **Crash recovery actually recovers**: after a content crash or background memory reclaim the session was still flagged "open", so `GeckoSession.open()` was a silent no-op and the tab stayed blank forever. Recovery now closes the dead session first (the documented Firefox-engine pattern) before reopening it and restoring the saved `SessionState`.
- **Single-stream download resume corruption**: resuming a plain (non-segmented) download now starts over instead of appending when the server's `ETag` changed since the last session — appending mismatched bytes silently corrupted the file. The segmented path already had this guard.
- **Segmented download corruption when a server ignores `Range`**: a `200 OK` answer to a ranged segment request is now only accepted for a fresh segment that starts at byte 0; other segments previously could be written from the wrong file offset when a CDN stripped the `Range` header.
- **File-vault containment hardening**: the download vault's path checks now compare full path segments (canonical root + separator), so a sibling directory sharing a name prefix can never be created into, renamed into, or deleted by mistake.

## [1.4.0] - 2026-11-26

### Added
- **Dedicated Multi-Engine Download Manager**: Added full-featured 4-tab Download Manager (`All` | `Torrents` | `Media` | `Files`) supporting any HTTP/HTTPS files, media streams, `.torrent` files, `magnet:` links, and open GeckoView `WebResponse` streams (blobs/authenticated downloads).
- **Segmented Multi-Thread HTTP Engine (`OkHttp` + `RandomAccessFile`)**: Supports 1–16 parallel `Range` byte-segment connections with live thread segment inspection, ETags verification, pause/resume persistence across app restarts, KB/s bandwidth throttling, custom HTTP User-Agent, metered Wi-Fi-only queueing, and automatic Wi-Fi reconnect resume via `ConnectivityManager.NetworkCallback`.
- **Native BitTorrent Engine (`libtorrent4j`)**: Full peer-to-peer BitTorrent & Magnet URI engine supporting DHT/LSD, sequential streaming piece order, per-file priority/selection toggles, live peer/seed counts, configurable upload/download limits, post-completion seeding toggle, and persistent resume state (`arm64-v8a` & `x86_64`).
- **SAB File System & In-App File Explorer**: Added structured SAB Download Vault with full in-app directory browsing, folder creation, renaming, SHA-256 checksum verification, zero-permission `MediaStore.Downloads` export (`Downloads/Simple AI Browser`), custom Storage Access Framework (`OpenDocumentTree`) folder targeting, and `FileProvider` Open/Share intents.
- **Page Media & Link Grabber (`sab-bridge` + Compat)**: Scans live web pages for embedded `<video>`, `<audio>`, `<img>`, `magnet:` links, and `.torrent` / downloadable media URLs with category filtering and batch downloading.
- **Foreground Data-Sync Service & Comprehensive Settings**: Added `SabDownloadService` (`foregroundServiceType="dataSync"`) progress & completion notifications, confirmation prompts, auto-organization by type, and 18 customizable download preferences stored in DataStore.
- **Full Privacy Shield & Hardening Suite**: Added configurable Enhanced Tracking Protection levels (`Off` / `Standard` / `Strict`), real HTTP `DNT: 1` & `Sec-GPC: 1` headers (`webRequest.onBeforeSendHeaders` in `sab-bridge` 1.3.0 + GeckoView `setGlobalPrivacyControl`), `navigator.doNotTrack` / `navigator.globalPrivacyControl` DOM signals, Total Cookie Protection (`dFPI`), unused tracker & bounce tracking cookie purging, URL tracking parameter stripping (`utm_*`, `fbclid`, `gclid`, `igshid`, etc. + *Copy Clean Link*), Fingerprinting Resistance, Local Network Access (LNA) Protection, Safe Browsing toggle, HTTPS-Only enforcement, Encrypted DNS over HTTPS (DoH presets: Cloudflare / Quad9 / AdGuard / Custom + Strict mode), Clear Cookies & History on Exit, and one-tap Cookie Purge inside both `PrivacyShieldSheet` and `SettingsScreen`.
- **Built-in PDF Viewer (`SabPdfViewerOverlay`)**: Native Android `PdfRenderer` + `ParcelFileDescriptor` viewer with `LruCache` page rendering, support for local vault files, `content://` URIs, and HTTP PDFs, `1.0x–3.0x` zoom, page scrubber & jump dialog, Night Paper inverted reading mode, Share, and Open External fallback.
- **Built-in Media Player (`SabMediaPlayerOverlay`)**: Hardware-accelerated `Media3 ExoPlayer 1.11.1` (`media3-exoplayer` + `media3-exoplayer-hls`) supporting local files, vault downloads, HTTP streams, and HLS `.m3u8` video/audio playback with custom Compose controls, ±10s seek, `0.5x–2.0x` playback speed, mute, loop, immersive fullscreen toggle, and lifecycle pause/release.
- **GNU GPL v3.0 License & Author Attribution**: Switched repository license to GNU GPL v3.0 with Section 7(b) attribution preservation crediting **Tamal Mitra (`Raj-Dev-Lab`)**, updated `NOTICE`, `README.md`, and added in-app *License & Author Attribution* details inside Settings.

## [1.3.0] - 2026-11-26

### Added
- **Extension Store (Mozilla Add-ons + GitHub WebExtensions)**: Added a full 3-tab Extension Manager & Store (`Mozilla Store`, `GitHub`, `Installed`) powered by Mozilla's AMO v5 API, GitHub repository/release `.xpi` discovery, and direct `.xpi` / `owner/repo` installation via GeckoView `WebExtensionController`.
- **Automatic & On-Demand Extension Updates**: Added startup auto-update checks (every 12 hours when enabled), native Mozilla update support (`WebExtensionController.update`), GitHub release asset version comparison, per-extension Auto-Update toggles, and a global setting in Settings.
- **Extension Popups, Options & Action Strip**: Added live browser-action toolbar strip in the Opera Menu with badge indicators, in-app `GeckoView` popup sheets (`ExtensionPopupSheet`), extension permission approval dialogs (`Allow` + `Allow in Private Tabs`), and options page navigation.
- **Full GeckoView Web Prompts, Permissions & File Pickers**: Added native Material 3 dialogs for HTTP Basic/Digest Auth, single/multi `<select>` choice lists, HTML `<input type="color">`, date/time pickers, page Share targets, Android `CAMERA`/`RECORD_AUDIO` runtime permission requests, and SAF file uploads (`OpenDocument` / `OpenMultipleDocuments`).

### Fixed
- **Zoom %, Reading Mode & Force Dark (`sab-bridge` 1.2.0)**: Added `"geckoViewAddons"` permission in `manifest.json`, switched `content.js` to reconnectable `connectNative("sabBridge")` ports with single atomic `SYNC_MODES` messages, added DOM-luminance-aware Force Dark (`isPageNativelyDark()`), full-page CSS zoom scaling (`75%–160%`), SPA `MutationObserver` re-application, and Readability-scored Reader Mode layout (`#sab-reader-overlay`).
- **GeckoView Extensions Runtime & Session Stability**: Enabled `extensionsWebAPIEnabled(true)` and `extensionsProcessEnabled(true)`, wired `setTabActive()` switching, implemented content crash/memory-reclaim recovery via `lastSessionState`, and streamed `WebResponse.body` downloads directly into `MediaStore.Downloads`.
- **Browser Reliability & Privacy**: Fixed FIFO prompt queuing with tab-close cancellation, external intent hydration barrier & `MainActivity` intent clearing on rotation, deduplicated History entries, safe *History & Caches Only* clearing that preserves logins/cookies, debounced DataStore tracker counters, and `WebView` compat mode improvements.

## [1.2.1] - 2026-11-26

### Changed
- **Real CI & reproducible builds**: the committed Gradle 9.3.1 wrapper (SHA-256-verified) and the one shared committed debug keystore replace the per-run random key, so debug APKs from every machine and from CI update in place. CI now publishes **per-ABI APKs** (`sab-*-arm64-v8a.apk` for phones, `sab-*-x86_64.apk` for emulators/Chromebooks) plus the universal APK under stable names, and verifies every APK's signature against the fixed certificate fingerprints before anything is attached. Signed release APKs build and attach automatically once the owner adds the three secrets from `docs/KEYSTORE.md` (until then that step is skipped with a notice — CI never turns red because of it).

### Added
- **AGENTS.md** with the project rules for contributors and AI agents, and a pointer to the private project memory.
- **Emulator launch smoke test** (`connectedDebugAndroidTest`) running in CI on an Android emulator (non-blocking).

### Fixed
- Release hygiene: a `v*` tag is refused unless it matches `versionName`, has a `CHANGELOG.md` section and raises `versionCode`; unit tests run **all** test classes; test and lint reports are uploaded on every run.

## [1.2.0] - 2026-11-25

### Added
- **Native Download Manager & Library Tab**: Wired GeckoView `ContentDelegate.onExternalResponse` and Android `DownloadManager` (zero storage permissions on Android 10+) with a new **Downloads** tab inside Library (`Saved` | `History` | `Files`) to inspect, open, and manage downloaded files.
- **One-Handed Bottom Address Bar Mode**: Added Opera-style **Top / Bottom Address Bar** layout switching in Settings so the omnibox and Find-in-Page bar can live directly above the bottom thumb-zone navigation bar.
- **Swipe Address Bar to Switch Tabs**: Swipe horizontally across the address bar pill to jump effortlessly between adjacent open tabs.
- **Session Persistence (Tabs & Tab Islands Restore)**: Upgraded Room database (`version 2` with non-destructive `MIGRATION_1_2`) to persist regular tabs and Tab Islands automatically and restore them on app relaunch (configurable in Settings).
- **Long-Press Link & Media Context Menu**: Wired GeckoView `onContextMenu` into `LinkContextBottomSheet` with *Open in New Tab*, *Open in Tab Island*, *Open in Private Tab*, *Copy Link*, *Download*, and *Share*.
- **Native Web Prompts & Site Permissions**: Wired GeckoView `PromptDelegate` (`alert`, `confirm`, `prompt`) and `PermissionDelegate` (location, notifications, DRM, storage access) into native Material 3 dialogs.
- **True Fullscreen Immersive Video Mode**: Wired GeckoView `onFullScreen` to hide browser chrome and Android system bars cleanly with transient swipe-to-reveal and Back-button exit.
- **Live Page Text Zoom (75%–160%)**: Added inline per-tab text scaling in the Opera Menu (via `sab-bridge` `SET_TEXT_ZOOM`), a live zoom badge in the address bar, and a Default Text Zoom preference in Settings.
- **Enhanced Omnibox & Find-in-Page**: Added one-tap Search Engine Switcher chips, quick copy current URL row, and Find-in-Page match counter (`current/total`) with Previous (`↑`) and Next (`↓`) controls.
- **Live Site Favicons & Tab Island Management**: Added Coil-backed `SiteFavicon` icons with instant monogram fallbacks across Speed Dial, Tab Gallery, Bookmarks, and History, plus Tab Island renaming and long-press tab-to-island reassignment.

## [1.1.1] - 2026-11-25

### Added
- **Dual 64-Bit ABI Support (`arm64-v8a` + `x86_64`)**: Switched to pre-compiled Mozilla GeckoView 161.0 non-omni filtered cleanly to `arm64-v8a` (Android 10+ devices) and `x86_64` (Android emulators) so native GeckoView runs directly on both phones and streaming emulators.
- **Automated GitHub Releases**: CI now automatically publishes downloadable `sab-debug-arm64-x86_64.apk` binaries on every `v*` tag release.

### Fixed
- **Android CI Workflow**: Upgraded GitHub Actions runner to JDK 21, Gradle `9.3.1`, and Android SDK `36.1` to match AGP 9.1.1 and `foojay-resolver-convention:1.0.0`.

## [1.1.0] - 2026-11-25

### Added
- **Native Android Browser Application (`com.simpleaibrowser.sab`)** targeting Android 10+ (`minSdk 29`, `targetSdk 36`).
- **Pre-compiled Mozilla GeckoView 161.0 (`geckoview-arm64-v8a`, non-omni)** rendering adapter with Enhanced Tracking Protection, Safe Browsing, and cookie isolation.
- **Built-in `sab-bridge` WebExtension** (`app/src/main/assets/extensions/sab-bridge/`) enabling Force Dark CSS injection, Reader Focus layout, and future DOM context snapshots.
- **Engine-Agnostic Core (`core/browser/`)** enforcing strict decoupling between the Compose UI shell and engine implementation, plus automatic host ABI compatibility guard.
- **Opera Mobile-Inspired UI Shell**:
  - 5-slot thumb-zone bottom navigation bar with Fast Action Search Orb and live Tab Counter badge.
  - Customizable 4-column Speed Dial Start Page with atmospheric wallpaper and Privacy Shield telemetry.
  - Tab Gallery with Regular, Tab Islands, and Private sections, Grid/List layout toggle, live tab search, automatic Tab Island grouping, and Recently Closed undo.
  - Opera Control Sheet, Privacy Shield Detail Sheet, Bookmarks & History Library, and Browser Settings with GeckoView Diagnostics.
- **Brand Identity & Motion**: Custom adaptive SAB prism logo, circular legacy mipmaps, bundled `Space Grotesk` / `Plus Jakarta Sans` / `JetBrains Mono` typography, 4 Material 3 theme presets, and animated orbital splash reveal.
- **Local Persistence**: Room database (`SabDatabase`) for Speed Dials, Bookmarks, and History; Jetpack DataStore for preferences and shield counters.
- **CI & Documentation**: GitHub Actions Android CI workflow, architecture deep-dive docs, GeckoView guide, and unit/Robolectric tests.

## [1.0.0] - 2026-11-24

### Added
- Initial architecture prototype, Opera UI layout analysis, and GeckoView 159+ technical blueprint.

## [0.1.0] - 2026-11-24

### Added
- Initial repository bootstrap, documentation structure, and MIT license.
