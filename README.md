# Simple AI Browser (SAB)

[![Android CI](https://github.com/Raj-Dev-Lab/Simple-Ai-Browser/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Raj-Dev-Lab/Simple-Ai-Browser/actions/workflows/android-ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-crimson.svg)](LICENSE)
[![Engine: GeckoView 161+](https://img.shields.io/badge/Engine-GeckoView%20161%20arm64--v8a-blue.svg)](docs/GECKOVIEW_ENGINE.md)
[![Platform: Android 10+](https://img.shields.io/badge/Android-10%2B%20(API%2029%2B)-3DDC84.svg)](app/build.gradle.kts)

**Simple AI Browser (SAB)** is an open-source, privacy-first Android web browser engineered with an **Opera Mobile-inspired ergonomic shell** on top of pre-compiled **Mozilla GeckoView 161+ (`geckoview-arm64-v8a`, non-omni)**.

Package ID: `com.simpleaibrowser.sab` · Minimum SDK: Android 10 (`API 29`) · Architecture: `arm64-v8a`

---

## Key Features (v1.1.0 — Core Browser Foundation)

- **Mozilla GeckoView 161+ Engine (`non-omni`, `arm64-v8a`)**: Full Gecko rendering with Enhanced Tracking Protection (ETP), Safe Browsing, anti-fingerprinting, and built-in `sab-bridge` WebExtension.
- **Engine-Agnostic Architecture**: Strict separation between the Compose UI shell (`ui/`), browser contracts (`core/browser/`), and the GeckoView adapter (`engine/gecko/`) — inspired by Opera Mobile's modular design.
- **Opera-Inspired Thumb-Zone Ergonomics**: 5-slot bottom navigation bar (`Back`, `Home`, glowing `Fast Action Search Orb`, `Tab Counter Badge`, `Control Menu`) designed for effortless one-handed use.
- **Speed Dial Start Page**: Atmospheric OLED start page with live Privacy Shield metrics and a customizable 4-column Speed Dial grid persisted locally in Room.
- **Redesigned Tab Gallery & Tab Islands**:
  - Automatic contextual grouping when links open in new tabs
  - Manual Tab Island creation, color-coding, collapsing, and bulk closing
  - Regular / Islands / Private switcher, Grid & List view toggle, tab search, and Recently Closed Tabs undo stack
- **Built-in Privacy Shield**: Blocks ads, analytics, social trackers, cryptominers, and fingerprinters with HTTPS-Only automatic upgrades and Private Mode.
- **Power-User Page Controls**: Desktop Site toggle, Force Dark Web Pages, Reader Focus Mode, Find in Page, Bookmarks, History, Native Share, and 4 curated Material 3 Theme Presets.
- **Signature Launch Reveal**: Custom adaptive SAB prism logo paired with an animated orbital intro sequence.

---

## Architecture Overview

```text
+----------------------------------------------------------------------+
|  PRESENTATION SHELL (Jetpack Compose + Material 3)                   |
|  Splash Reveal · Speed Dial · Omnibox · 5-Slot Bar · Tab Islands     |
+----------------------------------+-----------------------------------+
                                   | StateFlow / Commands
                                   v
+----------------------------------------------------------------------+
|  ENGINE-AGNOSTIC BROWSER CORE (core/browser)                         |
|  BrowserEngine · BrowserTabSession · UrlResolver · TabIsland Models  |
|  (Zero imports from org.mozilla.geckoview or android.webkit)         |
+----------------------------------+-----------------------------------+
                                   | Implemented by
                                   v
+----------------------------------------------------------------------+
|  ENGINE LAYER (engine/gecko)                                         |
|  org.mozilla.geckoview:geckoview-arm64-v8a:161.0 (non-omni, API 29+) |
|  GeckoRuntime · ETP ContentBlocking · sab-bridge WebExtension        |
+----------------------------------------------------------------------+
|  LOCAL DATA LAYER (data/)                                            |
|  Room Database (Speed Dial, Bookmarks, History) + DataStore Prefs    |
+----------------------------------------------------------------------+
```

Read the full deep-dive documentation:
- [System Architecture & Opera Parity Analysis](docs/ARCHITECTURE.md)
- [GeckoView 159+/161 Engine & WebExtension Guide](docs/GECKOVIEW_ENGINE.md)
- [Local Development, Build & Security Setup](docs/SETUP.md)

---

## Quick Start

### Prerequisites
- Android Studio Ladybug+ or JDK 17+ / Gradle 8.13+
- Android SDK Platform 36 (`minSdk 29` — Android 10+)
- ARM64 (`arm64-v8a`) physical device or emulator for native GeckoView execution

### Build & Run
```bash
git clone https://github.com/Raj-Dev-Lab/Simple-Ai-Browser.git
cd Simple-Ai-Browser
./gradlew :app:assembleDebug
```

Run unit & Robolectric tests:
```bash
./gradlew :app:testDebugUnitTest
```

---

## Roadmap Highlights

- **Phase 1 (`v1.1.0` — Current):** Production GeckoView 161+ browser core, Opera-inspired UI shell, Speed Dial, Tab Islands, Privacy Shield, Bookmarks & History.
- **Phase 2 (`v1.2.0`):** Native Download Manager, GeckoView `PromptDelegate` & runtime site permissions, fullscreen video & Picture-in-Picture, long-press link context menus.
- **Phase 3 (`v2.0.0`):** Modular AI Assistant layer (page summarization, selection Q&A, translation, Tab Island comparison) powered via `sab-bridge` DOM snapshots.

---

## Contributing & Security

- Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.
- Security issues and vulnerability disclosures are handled via [SECURITY.md](SECURITY.md).
- Track all releases in [CHANGELOG.md](CHANGELOG.md).

## License

Simple AI Browser (SAB) source code is licensed under the **MIT License** — see [LICENSE](LICENSE).  
Pre-compiled Mozilla GeckoView binaries are governed by the **Mozilla Public License 2.0 (MPL-2.0)**.
