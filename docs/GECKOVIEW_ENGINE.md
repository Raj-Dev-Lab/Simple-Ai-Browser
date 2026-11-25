# GeckoView 159+ Engine Integration Guide (ARM64, Non-Omni, Android 10+)

This specification defines how **Simple AI Browser (SAB)** integrates Mozilla's pre-compiled **GeckoView** rendering engine under strict target constraints:

- **Engine:** Pre-compiled Mozilla **GeckoView 159+** (`org.mozilla.geckoview`)
- **Variant:** **Standard non-omni (`geckoview-arm64-v8a`)** — *not* `geckoview-omni`
- **CPU ABI:** **ARM64 (`arm64-v8a`)** only
- **Minimum OS:** **Android 10 (`minSdk = 29`)**

---

## 1. Why `geckoview` (Non-Omni) Instead of `geckoview-omni`?

On `https://maven.mozilla.org/maven2/`, Mozilla publishes two parallel artifact families:

| Aspect | `geckoview-arm64-v8a` (SAB Choice) | `geckoview-omni-arm64-v8a` |
| --- | --- | --- |
| **Rendering & JS Engine** | Full Gecko + SpiderMonkey JIT (`arm64-v8a`) | Full Gecko + SpiderMonkey JIT (`arm64-v8a`) |
| **Mozilla Glean Telemetry & Rust Megazord** | **Excluded** (clean, zero Glean bloat) | Bundled inside the AAR for Fenix / `android-components` |
| **APK Size Impact** | Leaner native footprint (~40–55 MB compressed `arm64-v8a` only) | Larger due to bundled AC/Glean native symbols |
| **Browser Chrome Ownership** | 100% owned by SAB's custom shell (`:core-browser`) | Assumes `mozilla-android-components` (`browser-engine-gecko`) |
| **Privacy Alignment** | Zero built-in Mozilla telemetry pipelines | Ships Glean native telemetry bindings |

### Key Architectural Implication
Because SAB uses the **non-omni** `geckoview-arm64-v8a` artifact directly:
1. We do **not** pull in `mozilla.components:browser-engine-gecko` (which expects `-omni`).
2. SAB implements its own lightweight, Opera-inspired `:core-browser` shell layer directly over `GeckoRuntime` and `GeckoSession`.
3. Every browser feature—Tab Manager, Tab Islands, History, Bookmarks, Speed Dial, AdBlock stats, and SAB AI Bridge—is clean Kotlin code with zero hidden dependencies.

---

## 2. Gradle & Android Configuration (`arm64-v8a` + Android 10+)

### `settings.gradle.kts`
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "Mozilla"
            url = uri("https://maven.mozilla.org/maven2/")
        }
    }
}
```

### `gradle/libs.versions.toml`
```toml
[versions]
geckoview = "159.0.20261022150525" # Pin exact 159+ release/beta/nightly build from maven.mozilla.org

[libraries]
geckoview-arm64 = { group = "org.mozilla.geckoview", name = "geckoview-arm64-v8a", version.ref = "geckoview" }
```

### `app/build.gradle.kts` / `engine-gecko/build.gradle.kts`
```kotlin
android {
    namespace = "com.rajdevlab.sab"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rajdevlab.sab"
        minSdk = 29          // Android 10+
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            // Strictly target 64-bit ARM devices (arm64-v8a)
            abiFilters += listOf("arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.geckoview.arm64)
}
```

> **Note on Play Store / APK Distribution:** Filtering strictly to `arm64-v8a` keeps builds fast and APK sizes lean while covering virtually all Android 10+ phones released since 2019.

---

## 3. Runtime & Session Lifecycle Architecture

### Singleton `GeckoRuntime`
`GeckoRuntime` owns the native Gecko process, content processes (isolated e10s multi-process on Android 10+), HTTP cache, and `ContentBlocking` configuration. It must be initialized **once per Application process**:

```kotlin
val runtimeSettings = GeckoRuntimeSettings.Builder()
    .javaScriptEnabled(true)
    .webFontsEnabled(true)
    .automaticFontSizeAdjustment(true)
    .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM)
    .contentBlocking(
        ContentBlocking.Settings.Builder()
            .antiTracking(
                ContentBlocking.AntiTracking.AD or
                ContentBlocking.AntiTracking.ANALYTIC or
                ContentBlocking.AntiTracking.SOCIAL or
                ContentBlocking.AntiTracking.CRYPTOMINING or
                ContentBlocking.AntiTracking.FINGERPRINTING
            )
            .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS)
            .build()
    )
    .build()

val geckoRuntime = GeckoRuntime.create(applicationContext, runtimeSettings)
```

### Multi-Tab `GeckoSession` Pooling
- Each SAB tab owns one `GeckoSession` (with `usePrivateMode = true` in `GeckoSessionSettings` for Private Tabs).
- **Only the active foreground tab** is attached to the visible `GeckoView` surface (`geckoView.setSession(activeSession)`).
- Background tabs keep their `GeckoSession` open (`session.setActive(false)`) to save battery/RAM, or hibernate via `sessionState` serialization (`SessionState` Parcelable/JSON) when memory pressure occurs (`onTrimMemory`).

---

## 4. Essential Delegates for Opera-Style UI Parity

| GeckoView Delegate | What It Powers in SAB |
| --- | --- |
| `NavigationDelegate` | URL bar text, Back/Forward stack (`onCanGoBack`, `onCanGoForward`), external intent/deep-link handling (`onLoadRequest`) |
| `ProgressDelegate` | Top progress line (`onProgressChange`), page title (`onTitleChange`), TLS lock & security state (`onSecurityChange`) |
| `ContentDelegate` | Favicon loading, full-screen video (`onFullScreen`), context menu long-press (`onContextMenu`), crash recovery (`onCrash`) |
| `ScrollDelegate` | Opera-style smooth auto-hide / reveal of the bottom bar and compact top bar (`onScrollChanged`) |
| `SelectionActionDelegate` | Adds `"Ask SAB AI"` & `"Summarize"` directly to Android's text selection popup menu |
| `ContentBlocking.Delegate` | Live counter of blocked ads & trackers displayed in the Opera-style Shield / Menu Sheet |
| `PermissionDelegate` | Camera, mic, geolocation, and notification prompts rendered as clean bottom sheets |
| `PromptDelegate` | File picker (`<input type="file">`), JS alerts/confirms, color/date pickers, auth dialogs |

---

## 5. Built-In WebExtension Bridge (`sab-bridge`)

Because `GeckoSession` runs web content in an isolated content process, SAB bundles a tiny, manifest-v2/v3 **built-in WebExtension** inside `assets/extensions/sab-bridge/` registered via `runtime.webExtensionController.ensureBuiltIn(...)`:

1. **AI Page Context Extractor:** Extracts clean article text, `<title>`, meta description, headings, and selected text via Readability-style DOM traversal and streams it over `Port` messaging to `:core-ai`.
2. **Force Night Mode / Reader Mode:** Injects clean CSS overrides or Reader View typography when toggled from the Opera-style Bottom Menu Sheet.
3. **Cookie Banner & Cosmetic Ad Hiding:** Supplements GeckoView's network-level `ContentBlocking` with lightweight cosmetic CSS rules.
