export interface SpeedDialSite {
  id: string;
  name: string;
  domain: string;
  shortLabel: string;
  accent: string;
  island: string;
  title: string;
  summaryPoints: string[];
  bodyParagraphs: string[];
  blockedCount: number;
}

export const SPEED_DIAL_SITES: SpeedDialSite[] = [
  {
    id: 'wiki',
    name: 'Wikipedia',
    domain: 'en.wikipedia.org',
    shortLabel: 'W',
    accent: '#38BDF8',
    island: 'Research',
    title: 'Gecko (software) — Rendering Engine Architecture',
    summaryPoints: [
      'Gecko separates layout, DOM, and SpiderMonkey JS execution across isolated content processes.',
      'Android embedding uses GeckoView with explicit GeckoRuntime and GeckoSession lifecycle hooks.',
      'Non-omni builds omit Firefox-specific Glean telemetry and Megazord components.',
    ],
    bodyParagraphs: [
      'Gecko is a free and open-source browser engine developed by Mozilla. On Android 10+ devices targeting arm64-v8a, GeckoView exposes a deterministic Java/Kotlin API surface rather than wrapping legacy WebView hooks.',
      'In a decoupled browser shell like Opera Mobile, UI chrome components observe immutable navigation state streams while the engine adapter manages process isolation, TLS verification, and network-level content blocking.',
    ],
    blockedCount: 3,
  },
  {
    id: 'github',
    name: 'GitHub',
    domain: 'github.com',
    shortLabel: 'GH',
    accent: '#A78BFA',
    island: 'Engineering',
    title: 'Raj-Dev-Lab / Simple-Ai-Browser (SAB)',
    summaryPoints: [
      'Multi-module Android blueprint separating :core-browser from :engine-gecko.',
      'Targets minSdk 29 (Android 10+) and strictly arm64-v8a native binaries.',
      'Bundled sab-bridge WebExtension extracts DOM text safely for AI context.',
    ],
    bodyParagraphs: [
      'Simple AI Browser (SAB) pairs Opera-inspired thumb ergonomics—bottom navigation bar, Speed Dial, Tab Islands, and contextual AI sheet—with Mozilla pre-compiled GeckoView 159+.',
      'By keeping org.mozilla.geckoview:geckoview-arm64-v8a isolated inside :engine-gecko, UI modules never couple directly to GeckoSession internals.',
    ],
    blockedCount: 1,
  },
  {
    id: 'mdn',
    name: 'MDN Docs',
    domain: 'developer.mozilla.org',
    shortLabel: 'M',
    accent: '#F59E0B',
    island: 'Engineering',
    title: 'WebExtensions Native Messaging & Content Scripts',
    summaryPoints: [
      'Built-in extensions run in an isolated world with zero direct page script interference.',
      'Port messaging streams extracted article text cleanly to Kotlin coroutines.',
      'Supports Reader Mode CSS and Force Dark injection at document_idle.',
    ],
    bodyParagraphs: [
      'GeckoView executes page scripts inside dedicated content processes. To inspect DOM structure or extract readable text without brittle javascript: URLs, a built-in WebExtension communicates via runtime.connectNative.',
      'This pattern guarantees deterministic cleanup across tab switches and avoids memory leaks when background sessions hibernate.',
    ],
    blockedCount: 2,
  },
  {
    id: 'mozilla',
    name: 'Maven',
    domain: 'maven.mozilla.org',
    shortLabel: 'MZ',
    accent: '#FF2E4C',
    island: 'Engine',
    title: 'org.mozilla.geckoview — Artifact Variants',
    summaryPoints: [
      'geckoview-arm64-v8a ships pure Gecko + SpiderMonkey for 64-bit ARM devices.',
      'geckoview-omni adds Mozilla Android Components and Glean telemetry binaries.',
      'Version 159+ artifacts follow Major.0.YYYYMMDDHHMMSS build stamps.',
    ],
    bodyParagraphs: [
      'Mozilla publishes architecture-split AAR artifacts so custom Android browsers avoid shipping unused x86 or 32-bit ARM binaries. Targeting arm64-v8a alone significantly lowers final APK download weight.',
      'Choosing the standard non-omni variant keeps SAB completely free of Firefox telemetry frameworks while retaining full hardware-accelerated WebRender.',
    ],
    blockedCount: 0,
  },
  {
    id: 'arxiv',
    name: 'arXiv',
    domain: 'arxiv.org',
    shortLabel: 'AX',
    accent: '#10B981',
    island: 'Research',
    title: 'Ergonomic Reachability in Large-Display Mobile Browsers',
    summaryPoints: [
      'Anchoring primary controls in the bottom 35% reduces thumb travel by over 60%.',
      '5-slot bottom bar balances single-tap reach with visual clarity.',
      'Auto-hiding chrome on scroll preserves 88%+ vertical reading area.',
    ],
    bodyParagraphs: [
      'One-handed mobile interaction studies show that top-edge address bars sit well outside natural thumb arcs on 6.5-inch screens. Bottom-anchored navigation and unified omnibox triggers dramatically reduce grip shifts.',
      'Opera Mobile pioneered this pattern through its bottom navigation strip, centralized AI trigger, and swipe-to-switch tab affordances.',
    ],
    blockedCount: 4,
  },
  {
    id: 'ddg',
    name: 'DuckDuckGo',
    domain: 'duckduckgo.com',
    shortLabel: 'D',
    accent: '#FB923C',
    island: 'Search',
    title: 'Private Search & Tracking Protection Defaults',
    summaryPoints: [
      'ContentBlocking.AntiTracking blocks ad, analytic, social, and fingerprinting scripts.',
      'Private Tabs instantiate GeckoSession with usePrivateMode = true.',
      'Cookie behavior defaults to ACCEPT_NON_TRACKERS for balanced compatibility.',
    ],
    bodyParagraphs: [
      'Combining privacy-respecting search defaults with GeckoView Enhanced Tracking Protection stops third-party trackers before network requests leave the device.',
      'Blocked counts surface in the Opera-style bottom menu sheet so users have transparent, real-time visibility.',
    ],
    blockedCount: 7,
  },
];

export const OPERA_TO_GECKOVIEW_MAP = [
  {
    feature: 'Auto-hide bottom bar on scroll',
    operaSource: 'BrowserControlsStateProvider (Blink)',
    geckoTarget: 'GeckoSession.ScrollDelegate.onScrollChanged()',
  },
  {
    feature: 'Top loading progress & TLS lock',
    operaSource: 'TabObserver.onLoadProgressChanged',
    geckoTarget: 'ProgressDelegate.onProgressChange() & onSecurityChange()',
  },
  {
    feature: 'Visual Tab Gallery thumbnails',
    operaSource: 'TabContentManager.cacheTabThumbnail',
    geckoTarget: 'GeckoDisplay.capturePixels()',
  },
  {
    feature: 'Long-press text → Ask SAB AI',
    operaSource: 'SelectionPopupController',
    geckoTarget: 'GeckoSession.SelectionActionDelegate',
  },
  {
    feature: 'Ad & tracker blocked counter',
    operaSource: 'SubresourceFilter',
    geckoTarget: 'ContentBlocking.Delegate.onContentBlocked()',
  },
  {
    feature: 'Unified Omnibox & Back/Forward',
    operaSource: 'NavigationControllerImpl',
    geckoTarget: 'NavigationDelegate.onLocationChange() & onCanGoBack()',
  },
];

export const ARCHITECTURE_MODULES = [
  {
    id: 'app',
    name: ':app',
    role: 'Entry Point, DI Graph & Splash',
    description:
      'Hosts MainActivity, Android 12+ SplashScreen API integration, theme tokens, and wires feature modules to :core-browser.',
    codeSnippet: `// android/app/build.gradle.kts
defaultConfig {
    applicationId = "com.rajdevlab.sab"
    minSdk = 29 // Android 10+
    targetSdk = 35
    ndk {
        abiFilters += listOf("arm64-v8a")
    }
}`,
  },
  {
    id: 'core-browser',
    name: ':core-browser',
    role: 'Opera-Style Shell Abstraction',
    description:
      'Pure Kotlin contracts (BrowserEngine, BrowserTabSession, TabUiState). Zero imports from GeckoView or Android WebView.',
    codeSnippet: `interface BrowserTabSession {
    val state: StateFlow<TabUiState>
    fun loadUrl(urlOrQuery: String)
    fun goBack()
    fun goForward()
    suspend fun extractPageText(maxChars: Int = 12_000): PageContextSnapshot
    fun setReaderMode(enabled: Boolean)
    fun release()
}`,
  },
  {
    id: 'engine-gecko',
    name: ':engine-gecko',
    role: 'GeckoView 159+ Non-Omni Adapter',
    description:
      'Wraps GeckoRuntime, GeckoSession pooling, ContentBlocking, and the built-in sab-bridge WebExtension for arm64-v8a.',
    codeSnippet: `// gradle/libs.versions.toml
geckoview = "159.0.20261022150525"
geckoview-arm64 = {
    group = "org.mozilla.geckoview",
    name = "geckoview-arm64-v8a",
    version.ref = "geckoview"
}`,
  },
  {
    id: 'feature-shell',
    name: ':feature-shell & :feature-tabs',
    role: 'Opera Mobile UI & Tab Islands',
    description:
      'Implements the 5-slot thumb bar, Speed Dial shapes (Squircle/Circle/Square), Tab Islands gallery, and Bottom Action Sheet.',
    codeSnippet: `// 5-Slot Bottom Bar Contract
// [Back/Home] · [Search] · [SAB AI Orb] · [Tabs N] · [Menu Sheet]
// Auto-collapses smoothly when ScrollDelegate reports downward delta`,
  },
];
