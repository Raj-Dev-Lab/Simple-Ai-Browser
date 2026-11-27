# Simple AI Browser (SAB)

[![Android CI](https://github.com/Raj-Dev-Lab/Simple-Ai-Browser/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Raj-Dev-Lab/Simple-Ai-Browser/actions/workflows/android-ci.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-crimson.svg)](LICENSE)
[![Engine: GeckoView 161+](https://img.shields.io/badge/Engine-GeckoView%20161%20non--omni-blue.svg)](docs/GECKOVIEW_ENGINE.md)
[![Platform: Android 10+](https://img.shields.io/badge/Android-10%2B%20(API%2029%2B)-3DDC84.svg)](app/build.gradle.kts)

**Simple AI Browser (SAB)** is an open-source, privacy-first Android web browser engineered with an **Opera Mobile-inspired ergonomic shell** on top of pre-compiled **Mozilla GeckoView 161+ (`non-omni`, `arm64-v8a` + `x86_64`)**.

Package ID: `com.simpleaibrowser.sab` · Minimum SDK: Android 10 (`API 29`) · ABIs: `arm64-v8a`, `x86_64`

---

## Key Features (v1.4.0 — Privacy Suite, Built-in Viewers, Download Manager & Extension Store)

- **Mozilla GeckoView 161+ Engine (`non-omni`, `arm64-v8a` & `x86_64`)**: Full Gecko rendering with configurable Enhanced Tracking Protection (`Off`/`Standard`/`Strict`), Do Not Track (`DNT: 1`), Global Privacy Control (`Sec-GPC: 1`), Total Cookie Protection (`dFPI`), URL tracking parameter stripping, anti-fingerprinting, Local Network Access Protection, Encrypted DNS over HTTPS (DoH), native fullscreen video, JS prompts, Android permission flows, and `sab-bridge` WebExtension (`1.3.0`).
- **Built-in PDF Viewer & Media3 ExoPlayer**: Open downloaded, vault, or online PDFs in `SabPdfViewerOverlay` (native Android `PdfRenderer` with zoom, page scrubber & night paper mode), and stream/play video, audio, and HLS `.m3u8` streams in `SabMediaPlayerOverlay` (`Media3 ExoPlayer 1.11.1`).
- **Extension Store & Auto-Updater (`Mozilla Store` + `GitHub`)**: Browse Mozilla Add-ons (AMO) and GitHub WebExtension releases, install directly into GeckoView, inspect toolbar popups/options, and keep extensions updated automatically.
- **Dedicated Versatile Download Manager (`All` | `Torrents` | `Media` | `Files`)**:
  - **Multi-Thread HTTP (`OkHttp` + `RandomAccessFile`)**: 1–16 parallel `Range` byte segments, pause/resume persistence, KB/s speed caps, custom User-Agent, metered Wi-Fi-only hold + auto-resume on Wi-Fi reconnect.
  - **Native BitTorrent (`libtorrent4j`)**: `magnet:` links and `.torrent` files with DHT/LSD, sequential streaming piece order, per-file priority selection, live peer/seed counts, and seeding controls.
  - **Page Media & Link Grabber**: Scans any live webpage for `<video>`, `<audio>`, `<img>`, `magnet:`, and downloadable links.
  - **SAB File System & Explorer**: Structured download vault with folder creation, rename, delete, SHA-256 checksum verification, zero-permission `MediaStore.Downloads` export (`Downloads/Simple AI Browser`), and custom SAF folder selection (`OpenDocumentTree`).
- **Opera-Inspired Thumb-Zone Ergonomics & Power Controls**: Top/Bottom address bar switching, swipe-to-switch tabs, Speed Dial with live favicons, Tab Islands + Session Restore, Find-in-Page, Live Page Text Zoom (`75%–160%`), Force Dark Pages, Reader Focus Mode, and 4 Material 3 themes.

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
- **Phase 2 (`v1.2.0` – `v1.4.0` — Completed):** Bottom Address Bar mode, Tab & Island Session Restore, Extension Store (`Mozilla AMO` + `GitHub`) with Auto-Updater, Multi-Thread HTTP + `libtorrent4j` BitTorrent Download Manager, Page Media Grabber, In-App File Explorer, and GNU GPL v3.0 Attribution.
- **Phase 3 (`v2.0.0`):** Modular AI Assistant layer (page summarization, selection Q&A, translation, Tab Island comparison) powered via `sab-bridge` DOM snapshots.

---

## Contributing & Security

- Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.
- Security issues and vulnerability disclosures are handled via [SECURITY.md](SECURITY.md).
- Track all releases in [CHANGELOG.md](CHANGELOG.md).

## License & Attribution

Simple AI Browser (SAB) source code is **Copyright © 2026 Tamal Mitra (Raj-Dev-Lab)** and licensed under the **GNU General Public License v3.0 (GPL-3.0)** — see [LICENSE](LICENSE) and [NOTICE](NOTICE).  
All forks, source redistributions, and compiled builds must retain author attribution (`Tamal Mitra / Raj-Dev-Lab`) and the [NOTICE](NOTICE) file.  
Third-party components: Mozilla GeckoView (**MPL-2.0**), libtorrent4j (**MIT**), and AndroidX/Jetpack/OkHttp (**Apache-2.0**).
