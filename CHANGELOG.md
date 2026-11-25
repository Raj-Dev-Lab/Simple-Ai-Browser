# Changelog

All notable changes to **Simple AI Browser (SAB)** are documented in this file.  
This project adheres to [Semantic Versioning](https://semver.org/) and [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

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
- **Brand Identity & Motion**: Custom adaptive SAB prism launcher icon, circular legacy mipmaps, bundled `Space Grotesk` / `Plus Jakarta Sans` / `JetBrains Mono` typography, 4 Material 3 theme presets, and animated orbital splash reveal.
- **Local Persistence**: Room database (`SabDatabase`) for Speed Dials, Bookmarks, and History; Jetpack DataStore for preferences and shield counters.
- **CI & Documentation**: GitHub Actions Android CI workflow, architecture deep-dive docs, GeckoView guide, and unit/Robolectric tests.

## [1.0.0] - 2026-11-24

### Added
- Initial architecture prototype, Opera UI layout analysis, and GeckoView 159+ technical blueprint.

## [0.1.0] - 2026-11-24

### Added
- Initial repository bootstrap, documentation structure, and MIT license.
