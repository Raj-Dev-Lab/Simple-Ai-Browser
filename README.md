# Simple AI Browser (SAB)

[![Android CI](https://github.com/Raj-Dev-Lab/Simple-Ai-Browser/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Raj-Dev-Lab/Simple-Ai-Browser/actions/workflows/android-ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-crimson.svg)](LICENSE)
[![Engine: GeckoView 161+](https://img.shields.io/badge/Engine-GeckoView%20161%20non--omni-blue.svg)](docs/GECKOVIEW_ENGINE.md)
[![Platform: Android 10+](https://img.shields.io/badge/Android-10%2B%20(API%2029%2B)-3DDC84.svg)](app/build.gradle.kts)

**Simple AI Browser (SAB)** is an open-source, privacy-first Android web browser engineered with an **Opera Mobile-inspired ergonomic shell** on top of pre-compiled **Mozilla GeckoView 161+ (`non-omni`, `arm64-v8a` + `x86_64`)**.

Package ID: `com.simpleaibrowser.sab` · Minimum SDK: Android 10 (`API 29`) · ABIs: `arm64-v8a`, `x86_64`

---

## Key Features (v1.2.0 — Full Browser Engine & Ergonomics Upgrade)

- **Mozilla GeckoView 161+ Engine (`non-omni`, `arm64-v8a` & `x86_64`)**: Full Gecko rendering with Enhanced Tracking Protection (ETP), Safe Browsing, anti-fingerprinting, native fullscreen video, JS prompts, site permission dialogs, download interception, and the built-in `sab-bridge` WebExtension.
- **Engine-Agnostic Architecture**: Strict separation between the Compose UI shell (`ui/`), browser contracts (`core/browser/`), and the GeckoView adapter (`engine/gecko/`) — inspired by Opera Mobile's modular design.
- **Opera-Inspired Thumb-Zone Ergonomics**:
  - 5-slot bottom navigation bar (`Back`, `Home`, glowing `Fast Action Search Orb`, `Tab Counter Badge`, `Control Menu`)
  - Optional **Bottom Address Bar Mode** for true one-handed browsing
  - **Swipe horizontally on the address bar** to switch between open tabs instantly
- **Speed Dial Start Page with Live Favicons**: Atmospheric OLED start page with live Privacy Shield telemetry, Coil-powered site favicons with monogram fallbacks, customizable 4-column Speed Dial tiles, and quick access to Private Mode, Bookmarks, History, Downloads, and Settings.
- **Redesigned Tab Gallery, Tab Islands & Session Restore**:
  - Automatic contextual grouping when links open in new tabs
  - Create, rename, collapse, assign tabs (long-press), and bulk-close color-coded Tab Islands
  - Automatic session persistence restores regular tabs and Tab Islands on relaunch
  - Regular / Islands / Private switcher, Grid & List views, live tab search, and Recently Closed Tabs undo
- **Native Download Manager & Long-Press Context Menus**: Android `DownloadManager` integration with zero storage permissions required on Android 10+, dedicated **Downloads** tab in Library, and long-press link/media menus (*Open in New Tab*, *Open in Tab Island*, *Open in Private Tab*, *Copy*, *Download*, *Share*).
- **Power-User Reading & Navigation Controls**: Live Page Text Zoom (`75%–160%`), Find-in-Page with match counter and Previous/Next navigation, one-tap Search Engine Switcher chips in the omnibox, Desktop Site mode, Force Dark Pages, Reader Focus Mode, and 4 Material 3 Theme Presets.
- **Signature Launch Reveal**: Custom adaptive SAB prism logo paired with an animated orbital intro sequence.

---

## Architecture Overview

```text
+----------------------------------------------------------------------+
|  PRESENTATION SHELL (Jetpack Compose + Material 3)                   |
|  Splash · Speed Dial · Top/Bottom Omnibox · 5-Slot Bar · Tab Islands |
|  Context Sheets · Web Prompts · Fullscreen Video · Downloads Library |
+----------------------------------+-----------------------------------+
                                   | StateFlow / Commands
                                   v
+----------------------------------------------------------------------+
|  ENGINE-AGNOSTIC BROWSER CORE (core/browser)                         |
|  BrowserEngine · BrowserTabSession · SessionCallbacks · UrlResolver  |
|  (Zero imports from org.mozilla.geckoview or android.webkit)         |
+----------------------------------+-----------------------------------+
                                   | Implemented by
                                   v
+----------------------------------------------------------------------+
|  ENGINE LAYER (engine/gecko)                                         |
|  org.mozilla.geckoview:geckoview:161.0 (non-omni, arm64-v8a+x86_64)  |
|  GeckoRuntime · ETP ContentBlocking · Prompt/Permission/Download API |
|  sab-bridge WebExtension (Force Dark · Reader Focus · Text Zoom)     |
+----------------------------------------------------------------------+
|  LOCAL DATA LAYER (data/)                                            |
|  Room v2 (Speed Dial, Bookmarks, History, Downloads, Session Tabs)   |
|  Jetpack DataStore (Preferences & Privacy Shield Counters)           |
+----------------------------------------------------------------------+
```

Read the full deep-dive documentation:
- [System Architecture & Opera Parity Analysis](docs/ARCHITECTURE.md)
- [GeckoView 159+/161 Engine & WebExtension Guide](docs/GECKOVIEW_ENGINE.md)
- [Local Development, Build & Security Setup](docs/SETUP.md)

---

## Quick Start

### Prerequisites
- JDK 21 & Gradle 9.3.1
- Android SDK Platform 36.1 (`minSdk 29` — Android 10+)
- `arm64-v8a` physical device or `x86_64` Android emulator

### Build & Run
```bash
git clone https://github.com/Raj-Dev-Lab/Simple-Ai-Browser.git
cd Simple-Ai-Browser
gradle :app:assembleDebug
```

Run unit tests:
```bash
gradle :app:testDebugUnitTest --tests "com.example.ExampleUnitTest"
```

---

## Roadmap Highlights

- **Phase 1 (`v1.1.x` — Completed):** Production GeckoView 161+ dual-ABI browser core, Opera-inspired UI shell, Speed Dial, Tab Islands, Privacy Shield, Bookmarks & History, automated GitHub Releases.
- **Phase 2 (`v1.2.0` — Current):** Native Download Manager & Downloads tab, Bottom Address Bar mode, Swipe-to-Switch Tabs, Tab & Island Session Restore, Long-Press Link/Media Context Menus, Native Web Prompts & Site Permissions, Immersive Fullscreen Video, Live Page Text Zoom, Favicons, and Find-in-Page match counter.
- **Phase 3 (`v2.0.0`):** Modular AI Assistant layer (page summarization, selection Q&A, translation, Tab Island comparison) powered via `sab-bridge` DOM snapshots.

---

## Contributing & Security

- Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.
- Security issues and vulnerability disclosures are handled via [SECURITY.md](SECURITY.md).
- Track all releases in [CHANGELOG.md](CHANGELOG.md).

## License

Simple AI Browser (SAB) source code is licensed under the **MIT License** — see [LICENSE](LICENSE).  
Pre-compiled Mozilla GeckoView binaries are governed by the **Mozilla Public License 2.0 (MPL-2.0)**.
