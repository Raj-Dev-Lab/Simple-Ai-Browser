# Changelog

All notable changes to **Simple AI Browser (SAB)** are documented in this file.  
This project adheres to [Semantic Versioning](https://semver.org/) and [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

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
