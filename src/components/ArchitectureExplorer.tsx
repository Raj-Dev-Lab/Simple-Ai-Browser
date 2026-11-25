import {useState} from 'react';
import {
  ARCHITECTURE_MODULES,
  OPERA_TO_GECKOVIEW_MAP,
} from '../data/blueprintData';
import {SabLogo} from './SabLogo';

type ExplorerTab = 'opera' | 'gecko' | 'modules' | 'brand';

interface ArchitectureExplorerProps {
  onReplaySplash: () => void;
}

export function ArchitectureExplorer({onReplaySplash}: ArchitectureExplorerProps) {
  const [activeTab, setActiveTab] = useState<ExplorerTab>('opera');
  const [selectedModuleId, setSelectedModuleId] = useState<string>(ARCHITECTURE_MODULES[0].id);

  const selectedModule =
    ARCHITECTURE_MODULES.find((m) => m.id === selectedModuleId) || ARCHITECTURE_MODULES[0];

  return (
    <div className="space-y-6">
      {/* Segmented Navigation Tabs */}
      <div className="inline-flex flex-wrap items-center gap-1 p-1 rounded-xl bg-[#141821] border border-white/10">
        {(
          [
            {id: 'opera', label: '01. Opera UI Shell'},
            {id: 'gecko', label: '02. GeckoView 159+'},
            {id: 'modules', label: '03. Engine Swap'},
            {id: 'brand', label: '04. Logo & Splash'},
          ] as {id: ExplorerTab; label: string}[]
        ).map((tab) => (
          <button
            key={tab.id}
            type="button"
            onClick={() => setActiveTab(tab.id)}
            className={`px-3.5 py-2 rounded-lg text-xs font-medium transition-colors whitespace-nowrap shrink-0 ${
              activeTab === tab.id
                ? 'bg-[#FF2E4C] text-white'
                : 'text-slate-300 hover:text-white'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* TAB 1: OPERA MOBILE UI & SHELL MAPPING */}
      {activeTab === 'opera' && (
        <div id="opera-ui" className="space-y-6">
          <div className="rounded-2xl border border-white/10 bg-[#141821] p-6 space-y-4">
            <div className="space-y-1">
              <div className="text-xs text-slate-400">
                Thumb Ergonomics · 5-Slot Bottom Bar · Tab Islands
              </div>
              <h2 className="text-xl font-semibold text-white">
                Opera Mobile Shell Replicated on GeckoView
              </h2>
            </div>
            <p className="text-sm text-slate-300 leading-relaxed">
              Opera for Android eliminates top-bar reach fatigue by placing navigation, search,
              Tab Gallery, and the central AI button inside the bottom 35% Natural Thumb Zone.
              SAB copies this exact shell while replacing Blink/Chromium callbacks with GeckoView
              delegates.
            </p>
          </div>

          <div className="rounded-2xl border border-white/10 bg-[#141821] overflow-hidden">
            <div className="px-6 py-4 border-b border-white/10">
              <h3 className="text-sm font-semibold text-white">
                Opera Chromium Hooks vs. SAB GeckoView 159+ Callbacks
              </h3>
            </div>
            <div className="divide-y divide-white/8">
              {OPERA_TO_GECKOVIEW_MAP.map((row) => (
                <div
                  key={row.feature}
                  className="px-6 py-3.5 grid grid-cols-1 md:grid-cols-3 gap-2 text-xs"
                >
                  <div className="font-medium text-white">{row.feature}</div>
                  <div className="text-slate-400 font-mono">{row.operaSource}</div>
                  <div className="text-sky-300 font-mono">{row.geckoTarget}</div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* TAB 2: GECKOVIEW 159+ NON-OMNI ARM64 */}
      {activeTab === 'gecko' && (
        <div id="geckoview" className="space-y-6">
          <div className="rounded-2xl border border-white/10 bg-[#141821] p-6 space-y-4">
            <div className="text-xs text-slate-400">
              org.mozilla.geckoview · Non-Omni · arm64-v8a · minSdk 29
            </div>
            <h2 className="text-xl font-semibold text-white">
              Why Pre-Compiled Non-Omni GeckoView 159+ (ARM64)
            </h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-1">
              <div className="rounded-xl border border-white/10 bg-[#0B0D12] p-4 space-y-2">
                <div className="text-sm font-semibold text-white">geckoview-arm64-v8a (SAB)</div>
                <p className="text-xs text-slate-400 leading-relaxed">
                  Pure Gecko rendering + SpiderMonkey JS engine for 64-bit ARM. Omits Glean
                  telemetry and Mozilla Android Components megazord binaries for a leaner APK and
                  100% custom Opera-style Kotlin shell control.
                </p>
              </div>
              <div className="rounded-xl border border-white/10 bg-[#0B0D12] p-4 space-y-2">
                <div className="text-sm font-semibold text-slate-300">
                  geckoview-omni (Skipped)
                </div>
                <p className="text-xs text-slate-400 leading-relaxed">
                  Bundles Fenix/AC-specific Rust megazord libraries and Glean telemetry hooks.
                  Unnecessary weight when building a clean custom browser shell from scratch.
                </p>
              </div>
            </div>
          </div>

          <div className="rounded-2xl border border-white/10 bg-[#141821] p-6 space-y-3">
            <h3 className="text-sm font-semibold text-white">
              Verified Gradle Configuration (Android 10+ &amp; ARM64)
            </h3>
            <pre className="rounded-xl bg-[#0B0D12] border border-white/10 p-4 overflow-x-auto text-xs font-mono text-slate-200 leading-relaxed">
{`// settings.gradle.kts
maven { url = uri("https://maven.mozilla.org/maven2/") }

// gradle/libs.versions.toml
geckoview = "159.0.20261022150525"
geckoview-arm64 = "org.mozilla.geckoview:geckoview-arm64-v8a:159.0.20261022150525"

// app/build.gradle.kts
defaultConfig {
    minSdk = 29 // Android 10+
    targetSdk = 35
    ndk { abiFilters += listOf("arm64-v8a") }
}`}
            </pre>
          </div>
        </div>
      )}

      {/* TAB 3: ENGINE-SWAP ARCHITECTURE */}
      {activeTab === 'modules' && (
        <div id="architecture" className="space-y-6">
          <div className="rounded-2xl border border-white/10 bg-[#141821] p-6 space-y-4">
            <div className="text-xs text-slate-400">
              Decoupled Kotlin Multi-Module Blueprint · android/
            </div>
            <h2 className="text-xl font-semibold text-white">
              Opera Shell Boundary + GeckoView Adapter
            </h2>
            <p className="text-sm text-slate-300 leading-relaxed">
              Just like Opera Mobile isolates its UI chrome from Chromium&apos;s WebContents,
              SAB places all UI modules above <code className="font-mono text-sky-300">:core-browser</code>.
              Select a module below to inspect its contract in <code className="font-mono text-sky-300">android/</code>:
            </p>

            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-1">
              {ARCHITECTURE_MODULES.map((mod) => (
                <button
                  key={mod.id}
                  type="button"
                  onClick={() => setSelectedModuleId(mod.id)}
                  className={`px-3 py-2.5 rounded-xl border text-left transition-colors ${
                    selectedModule.id === mod.id
                      ? 'border-[#FF2E4C] bg-[#FF2E4C]/10 text-white'
                      : 'border-white/10 bg-[#0B0D12] text-slate-300 hover:border-white/25'
                  }`}
                >
                  <div className="text-xs font-mono font-semibold truncate">{mod.name}</div>
                  <div className="text-[11px] text-slate-400 truncate mt-0.5">{mod.role}</div>
                </button>
              ))}
            </div>
          </div>

          <div className="rounded-2xl border border-white/10 bg-[#141821] p-6 space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold text-white">
                {selectedModule.name} — {selectedModule.role}
              </h3>
            </div>
            <p className="text-xs text-slate-300 leading-relaxed">{selectedModule.description}</p>
            <pre className="rounded-xl bg-[#0B0D12] border border-white/10 p-4 overflow-x-auto text-xs font-mono text-slate-200 leading-relaxed">
              {selectedModule.codeSnippet}
            </pre>
          </div>
        </div>
      )}

      {/* TAB 4: BRAND LOGO & SPLASH MOTION SPEC */}
      {activeTab === 'brand' && (
        <div id="brand" className="space-y-6">
          <div className="rounded-2xl border border-white/10 bg-[#141821] p-6 flex flex-col sm:flex-row items-start sm:items-center gap-6">
            <SabLogo size={112} className="rounded-3xl" />
            <div className="space-y-3">
              <div className="text-xs text-slate-400">
                Adaptive Icon Safe Zone · Obsidian &amp; Crimson Coral
              </div>
              <h2 className="text-xl font-semibold text-white">
                SAB Emblem &amp; Splash Motion Spec
              </h2>
              <p className="text-sm text-slate-300 leading-relaxed">
                Designed around an interlocking crimson-and-silver orbital S-aperture framing a
                precision compass star. On Android 10–15, the intro animates via compositor scale,
                rotation, and a 650ms–1.2s engine-boot progress sweep.
              </p>
              <button
                type="button"
                onClick={onReplaySplash}
                className="px-4 py-2 rounded-lg bg-[#FF2E4C] text-white text-xs font-semibold hover:bg-[#e62440] transition-colors"
              >
                Replay Splash Animation
              </button>
            </div>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            {[
              {name: 'Obsidian Canvas', hex: '#0B0D12'},
              {name: 'Chrome Surface', hex: '#141821'},
              {name: 'SAB Crimson', hex: '#FF2E4C'},
              {name: 'TLS Cyan', hex: '#38BDF8'},
            ].map((swatch) => (
              <div
                key={swatch.hex}
                className="rounded-xl border border-white/10 bg-[#141821] p-3.5 space-y-2"
              >
                <div
                  className="h-10 rounded-lg border border-white/10"
                  style={{backgroundColor: swatch.hex}}
                />
                <div className="text-xs font-medium text-white">{swatch.name}</div>
                <div className="text-[11px] font-mono text-slate-400 tabular-nums">{swatch.hex}</div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
