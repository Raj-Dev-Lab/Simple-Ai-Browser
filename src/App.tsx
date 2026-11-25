/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import {useState} from 'react';
import {ArchitectureExplorer} from './components/ArchitectureExplorer';
import {MobileBrowserPrototype} from './components/MobileBrowserPrototype';
import {SabLogo} from './components/SabLogo';

export default function App() {
  const [splashRunId, setSplashRunId] = useState(1);

  const handleReplaySplash = () => {
    setSplashRunId((n) => n + 1);
  };

  return (
    <div className="min-h-screen bg-[#0B0D12] text-slate-100 flex flex-col">
      {/* 3-Zone Top Bar Contract */}
      <header className="border-b border-white/10 bg-[#0B0D12]/95 sticky top-0 z-30 backdrop-blur-md">
        <div className="mx-auto max-w-7xl px-6 py-4 flex items-center justify-between gap-8">
          <a
            href="#top"
            className="text-base font-bold tracking-tight text-white whitespace-nowrap shrink-0"
          >
            Simple AI Browser
          </a>

          <nav className="hidden md:flex items-center gap-6 text-sm font-medium text-slate-400">
            <a href="#workbench" className="hover:text-white transition-colors whitespace-nowrap shrink-0">
              Opera UI
            </a>
            <a href="#workbench" className="hover:text-white transition-colors whitespace-nowrap shrink-0">
              GeckoView 159+
            </a>
            <a href="#workbench" className="hover:text-white transition-colors whitespace-nowrap shrink-0">
              Architecture
            </a>
            <a href="#specs" className="hover:text-white transition-colors whitespace-nowrap shrink-0">
              Specs
            </a>
          </nav>

          <button
            type="button"
            onClick={handleReplaySplash}
            className="px-4 py-2 rounded-lg bg-[#FF2E4C] text-white text-xs font-semibold hover:bg-[#e62440] transition-colors whitespace-nowrap shrink-0"
          >
            Replay Splash
          </button>
        </div>
      </header>

      <main id="top" className="flex-1 mx-auto w-full max-w-7xl px-6 py-10 space-y-12">
        {/* Hero Overview */}
        <section className="flex flex-col lg:flex-row lg:items-center justify-between gap-8 border-b border-white/10 pb-10">
          <div className="space-y-4 max-w-2xl">
            <div className="text-xs font-medium text-slate-400">
              SAB · Opera Mobile Shell · GeckoView 159+ Non-Omni · arm64-v8a · Android 10+
            </div>
            <h1 className="text-3xl sm:text-4xl font-bold tracking-tight text-white text-balance">
              Simple AI Browser Architecture &amp; Interactive Opera-Style UI
            </h1>
            <p className="text-base text-slate-300 leading-relaxed">
              Deep teardown of Opera for Android&apos;s thumb-first navigation shell paired with a
              decoupled Mozilla GeckoView 159+ engine adapter for 64-bit ARM devices. Test the SAB
              splash animation, Speed Dial, Tab Islands, and bottom-bar ergonomics live on the right.
            </p>
          </div>

          <div className="flex items-center gap-4 shrink-0 rounded-2xl border border-white/10 bg-[#141821] p-4">
            <SabLogo size={64} className="rounded-2xl" />
            <div className="space-y-1">
              <div className="text-sm font-semibold text-white">SAB Emblem</div>
              <div className="text-xs text-slate-400 tabular-nums">
                minSdk 29 · arm64-v8a
              </div>
            </div>
          </div>
        </section>

        {/* Split Workbench: Interactive Mobile Prototype + Architecture Explorer */}
        <section
          id="workbench"
          className="grid grid-cols-1 lg:grid-cols-12 gap-10 items-start"
        >
          <div className="lg:col-span-7 space-y-8">
            <ArchitectureExplorer onReplaySplash={handleReplaySplash} />

            <div
              id="specs"
              className="rounded-2xl border border-white/10 bg-[#141821] p-6 grid grid-cols-2 sm:grid-cols-4 gap-6"
            >
              <div className="space-y-1">
                <div className="text-xs text-slate-400">Rendering Engine</div>
                <div className="text-sm font-semibold text-white tabular-nums">GeckoView 159+</div>
              </div>
              <div className="space-y-1">
                <div className="text-xs text-slate-400">Artifact Variant</div>
                <div className="text-sm font-semibold text-white">Non-Omni</div>
              </div>
              <div className="space-y-1">
                <div className="text-xs text-slate-400">CPU Target</div>
                <div className="text-sm font-semibold text-white font-mono">arm64-v8a</div>
              </div>
              <div className="space-y-1">
                <div className="text-xs text-slate-400">Minimum OS</div>
                <div className="text-sm font-semibold text-white tabular-nums">Android 10 (API 29)</div>
              </div>
            </div>
          </div>

          <div className="lg:col-span-5 lg:sticky lg:top-24">
            <MobileBrowserPrototype
              splashRunId={splashRunId}
              onReplaySplash={handleReplaySplash}
            />
          </div>
        </section>
      </main>

      <footer className="border-t border-white/10 py-6 mt-8">
        <div className="mx-auto max-w-7xl px-6 flex flex-col sm:flex-row items-center justify-between gap-4 text-xs text-slate-400">
          <span>Simple AI Browser (SAB) · Apache-2.0 Open Source</span>
          <span>Docs: docs/ARCHITECTURE.md · docs/GECKOVIEW_ENGINE.md · docs/OPERA_UI_ANALYSIS.md</span>
        </div>
      </footer>
    </div>
  );
}
