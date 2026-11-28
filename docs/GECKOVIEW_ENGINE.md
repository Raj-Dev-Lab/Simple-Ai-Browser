# Mozilla GeckoView 161 Engine Guide

## Artifact Specification

SAB links directly against Mozilla's official pre-compiled **omni** artifact from Mozilla Maven:

- **Repository:** `https://maven.mozilla.org/maven2/`
- **Coordinate:** `org.mozilla.geckoview:geckoview:161.0.20261118180411`
- **Minimum SDK:** Android 10 (`minSdk = 29`)
- **Variant:** Omni — the single AAR ships native libraries for `arm64-v8a` (64-bit ARMv8 devices) and `x86_64` (Android emulators); the build additionally constrains ABIs via `splits.abi` / `abiFilters`. There is no separate "non-omni" artifact in this project.

## Runtime Configuration (`GeckoEngineAdapter.kt`)

`GeckoRuntime` is initialized once as a process-wide singleton with:
- **JavaScript & WebFonts:** Enabled
- **Automatic Font Size Adjustment:** Enabled for system accessibility scaling
- **Enhanced Tracking Protection (ETP):**
  - `ContentBlocking.AntiTracking.AD`
  - `ContentBlocking.AntiTracking.ANALYTIC`
  - `ContentBlocking.AntiTracking.SOCIAL`
  - `ContentBlocking.AntiTracking.CRYPTOMINING`
  - `ContentBlocking.AntiTracking.FINGERPRINTING`
- **Safe Browsing:** `ContentBlocking.SafeBrowsing.DEFAULT`
- **Cookie Policy:** `ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS`

## Built-in WebExtension (`sab-bridge`)

Located in `app/src/main/assets/extensions/sab-bridge/`:
- Registered via `runtime.webExtensionController.ensureBuiltIn("resource://android/assets/extensions/sab-bridge/", "sab-bridge@simpleaibrowser.com")`
- Connects over native messaging port `sabBridge`
- Handles:
  1. `SET_FORCE_DARK` — clean inverted OLED CSS preserving images/videos/canvas
  2. `SET_READER_MODE` — distraction-free centered reading typography
  3. `EXTRACT_SNAPSHOT` — structured URL, title, body text, and selection extraction for future Phase 3 AI features
