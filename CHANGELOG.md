# Changelog

All notable changes to **Simple AI Browser (SAB)** are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and uses [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-11-25

### Added
- **Full-Stack Live Web Browser (`server.ts` + `src/`):**
  - SSRF-hardened live webpage fetcher (`POST /api/browse`) with private/loopback IP blocking, tracker/script stripping, and safe structured DOM rendering.
  - Live DuckDuckGo web search (`POST /api/search`) and clickable result browsing.
  - Contextual **SAB AI** assistant (`POST /api/ai`) powered by `@google/genai` (`gemini-4-hydrogen`) reading the active webpage or using Google Search grounding.
  - Opera-Mobile-inspired shell: 5-slot bottom navigation bar, customizable Speed Dial (Squircle/Circle/Square tiles + custom shortcuts), Tab Gallery with Tab Islands & Private Tabs, Bookmarks & History persistence, Reader Mode, Desktop UA toggle, Find in Page, and page text export.
- **SAB Brand & Splash Animation:** Custom geometric SAB emblem and choreographed boot splash screen.
- **Native Android GeckoView 159+ Blueprint (`android/`):** Multi-module Kotlin/Compose starter (`:app`, `:core-browser`, `:engine-gecko`) configured for `org.mozilla.geckoview:geckoview-arm64-v8a:159+` (non-omni), `minSdk = 29` (Android 10+), `arm64-v8a`, and `sab-bridge` WebExtension.
- **Repository Standards:** GitHub Actions CI workflow, issue/PR templates, and `CHANGELOG.md`.

## [0.1.0] - 2026-11-25

### Added
- Initial repository scaffold, Apache-2.0 `LICENSE`, `README.md`, `CONTRIBUTING.md`, `SECURITY.md`, and `docs/SETUP.md`.
