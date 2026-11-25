import {useEffect, useState} from 'react';
import {AnimatePresence, motion} from 'motion/react';
import {
  ArrowLeft,
  BookOpen,
  Check,
  EyeOff,
  Globe,
  Home,
  Lock,
  Menu,
  Monitor,
  Moon,
  Plus,
  RotateCcw,
  Search,
  Shield,
  Sparkles,
  Sun,
  X,
} from 'lucide-react';
import {SPEED_DIAL_SITES, type SpeedDialSite} from '../data/blueprintData';
import {SabLogo} from './SabLogo';
import {SplashOverlay} from './SplashOverlay';

type TileShape = 'squircle' | 'circle' | 'square';
type BrowserTheme = 'obsidian' | 'daylight';
type ActiveOverlay = 'none' | 'tabs' | 'menu' | 'ai' | 'omnibox';

interface BrowserTab {
  id: string;
  siteId: string | null;
  title: string;
  url: string;
  island: string;
  isPrivate: boolean;
  blockedCount: number;
}

interface MobileBrowserPrototypeProps {
  splashRunId: number;
  onReplaySplash: () => void;
}

export function MobileBrowserPrototype({
  splashRunId,
  onReplaySplash,
}: MobileBrowserPrototypeProps) {
  const [showSplash, setShowSplash] = useState(true);
  const [theme, setTheme] = useState<BrowserTheme>('obsidian');
  const [tileShape, setTileShape] = useState<TileShape>('squircle');
  const [adBlockEnabled, setAdBlockEnabled] = useState(true);
  const [cookieBlockerEnabled, setCookieBlockerEnabled] = useState(true);
  const [readerMode, setReaderMode] = useState(false);
  const [desktopMode, setDesktopMode] = useState(false);
  const [isPrivateMode, setIsPrivateMode] = useState(false);
  const [activeOverlay, setActiveOverlay] = useState<ActiveOverlay>('none');
  const [searchInput, setSearchInput] = useState('');
  const [loadingProgress, setLoadingProgress] = useState(0);

  const [tabs, setTabs] = useState<BrowserTab[]>([
    {
      id: 'tab-1',
      siteId: null,
      title: 'Speed Dial',
      url: 'sab://start',
      island: 'Workspace',
      isPrivate: false,
      blockedCount: 0,
    },
    {
      id: 'tab-2',
      siteId: 'wiki',
      title: 'Gecko (software) — Architecture',
      url: 'https://en.wikipedia.org/wiki/Gecko_(software)',
      island: 'Research',
      isPrivate: false,
      blockedCount: 3,
    },
    {
      id: 'tab-3',
      siteId: 'github',
      title: 'Raj-Dev-Lab / Simple-Ai-Browser',
      url: 'https://github.com/Raj-Dev-Lab/Simple-Ai-Browser',
      island: 'Engineering',
      isPrivate: false,
      blockedCount: 1,
    },
  ]);
  const [activeTabId, setActiveTabId] = useState<string>('tab-1');

  useEffect(() => {
    setShowSplash(true);
    const timer = window.setTimeout(() => {
      setShowSplash(false);
    }, 1700);
    return () => window.clearTimeout(timer);
  }, [splashRunId]);

  const visibleTabs = tabs.filter((t) => t.isPrivate === isPrivateMode);
  const activeTab =
    tabs.find((t) => t.id === activeTabId && t.isPrivate === isPrivateMode) ||
    visibleTabs[0] ||
    tabs[0];

  const activeSite: SpeedDialSite | undefined = SPEED_DIAL_SITES.find(
    (s) => s.id === activeTab?.siteId,
  );

  const triggerLoadAnimation = () => {
    setLoadingProgress(35);
    window.setTimeout(() => setLoadingProgress(85), 140);
    window.setTimeout(() => setLoadingProgress(100), 320);
    window.setTimeout(() => setLoadingProgress(0), 560);
  };

  const openSiteInActiveTab = (site: SpeedDialSite) => {
    triggerLoadAnimation();
    setTabs((prev) =>
      prev.map((tab) =>
        tab.id === activeTab.id
          ? {
              ...tab,
              siteId: site.id,
              title: site.title,
              url: `https://${site.domain}`,
              island: site.island,
              blockedCount: adBlockEnabled ? site.blockedCount : 0,
            }
          : tab,
      ),
    );
    setActiveOverlay('none');
  };

  const handleGoHome = () => {
    setTabs((prev) =>
      prev.map((tab) =>
        tab.id === activeTab.id
          ? {
              ...tab,
              siteId: null,
              title: 'Speed Dial',
              url: 'sab://start',
              blockedCount: 0,
            }
          : tab,
      ),
    );
    setActiveOverlay('none');
  };

  const handleCreateTab = (privateFlag = isPrivateMode) => {
    const newId = `tab-${Date.now()}`;
    const newTab: BrowserTab = {
      id: newId,
      siteId: null,
      title: privateFlag ? 'Private Start' : 'Speed Dial',
      url: 'sab://start',
      island: privateFlag ? 'Private' : 'Workspace',
      isPrivate: privateFlag,
      blockedCount: 0,
    };
    setIsPrivateMode(privateFlag);
    setTabs((prev) => [...prev, newTab]);
    setActiveTabId(newId);
    setActiveOverlay('none');
  };

  const handleCloseTab = (tabId: string) => {
    setTabs((prev) => {
      const remaining = prev.filter((t) => t.id !== tabId);
      if (remaining.length === 0) {
        const fallback: BrowserTab = {
          id: `tab-${Date.now()}`,
          siteId: null,
          title: 'Speed Dial',
          url: 'sab://start',
          island: 'Workspace',
          isPrivate: isPrivateMode,
          blockedCount: 0,
        };
        setActiveTabId(fallback.id);
        return [fallback];
      }
      if (activeTabId === tabId) {
        const sameMode = remaining.find((t) => t.isPrivate === isPrivateMode) || remaining[0];
        setActiveTabId(sameMode.id);
      }
      return remaining;
    });
  };

  const handleOmniboxSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const query = searchInput.trim().toLowerCase();
    if (!query) return;

    const matched =
      SPEED_DIAL_SITES.find(
        (s) =>
          s.name.toLowerCase().includes(query) ||
          s.domain.toLowerCase().includes(query) ||
          s.title.toLowerCase().includes(query),
      ) || SPEED_DIAL_SITES[0];

    openSiteInActiveTab(matched);
    setSearchInput('');
  };

  const shapeRadiusClass =
    tileShape === 'circle'
      ? 'rounded-full'
      : tileShape === 'square'
        ? 'rounded-xl'
        : 'rounded-[22px]';

  const isDark = theme === 'obsidian';
  const shellBg = isPrivateMode
    ? 'bg-[#120F1D] text-slate-100'
    : isDark
      ? 'bg-[#0B0D12] text-slate-100'
      : 'bg-[#F8FAFC] text-slate-900';
  const barBg = isPrivateMode
    ? 'bg-[#191429]/95 border-violet-500/20'
    : isDark
      ? 'bg-[#141821]/95 border-white/10'
      : 'bg-white/95 border-slate-200';
  const cardBg = isDark || isPrivateMode ? 'bg-[#171C26] border-white/8' : 'bg-white border-slate-200';
  const mutedText = isDark || isPrivateMode ? 'text-slate-400' : 'text-slate-500';

  return (
    <div className="flex flex-col items-center">
      {/* Device Controls Header */}
      <div className="w-full max-w-[390px] flex items-center justify-between mb-3 px-1">
        <span className="text-xs font-medium text-slate-400 tabular-nums">
          Android 10+ · arm64-v8a Shell Preview
        </span>
        <button
          type="button"
          onClick={onReplaySplash}
          className="inline-flex items-center gap-1.5 text-xs font-medium text-slate-300 hover:text-white transition-colors whitespace-nowrap shrink-0 px-2.5 py-1.5 rounded-lg border border-white/10 bg-[#141821]"
        >
          <RotateCcw className="w-3.5 h-3.5 text-[#FF2E4C]" />
          Replay Splash
        </button>
      </div>

      {/* Android 10+ Device Frame */}
      <div
        className={`relative w-full max-w-[390px] h-[740px] rounded-[40px] border-[6px] border-[#1E2432] shadow-2xl overflow-hidden flex flex-col ${shellBg}`}
      >
        {/* Top System Bar */}
        <div className="flex items-center justify-between px-6 pt-3 pb-2 text-[11px] font-medium tabular-nums shrink-0">
          <span>10:40</span>
          <div className="w-16 h-4 rounded-full bg-black/40 border border-white/10" />
          <span className="text-[10px] text-slate-400">5G · 94%</span>
        </div>

        {/* Active Page Compact Address Header (when viewing a webpage) */}
        {activeSite && (
          <div className={`px-4 py-2 border-b ${barBg} shrink-0`}>
            <button
              type="button"
              onClick={() => setActiveOverlay('omnibox')}
              className={`w-full flex items-center justify-between gap-2 px-3 py-2 rounded-xl border text-left ${cardBg}`}
            >
              <span className="flex items-center gap-2 min-w-0">
                <Lock className="w-3.5 h-3.5 text-sky-400 shrink-0" />
                <span className="text-xs font-medium truncate">{activeSite.domain}</span>
              </span>
              {adBlockEnabled && (
                <span className="text-[11px] text-emerald-400 font-mono tabular-nums shrink-0">
                  {activeTab.blockedCount} blocked
                </span>
              )}
            </button>
            {loadingProgress > 0 && (
              <div className="mt-1.5 h-0.5 w-full bg-slate-800 overflow-hidden rounded-full">
                <div
                  style={{width: `${loadingProgress}%`}}
                  className="h-full bg-[#FF2E4C] transition-all duration-150"
                />
              </div>
            )}
          </div>
        )}

        {/* Main Viewport Canvas */}
        <div className="flex-1 overflow-y-auto px-4 pt-3 pb-20">
          {!activeSite ? (
            /* OPERA-INSPIRED START PAGE & SPEED DIAL */
            <div className="space-y-6">
              <div className="flex items-center justify-between pt-1">
                <div className="flex items-center gap-2.5">
                  <SabLogo size={34} className="rounded-xl" />
                  <div>
                    <div className="text-sm font-semibold leading-tight">
                      {isPrivateMode ? 'Private SAB' : 'Simple AI Browser'}
                    </div>
                    <div className={`text-[11px] ${mutedText}`}>
                      GeckoView 159 · arm64-v8a
                    </div>
                  </div>
                </div>
                <div className="flex items-center gap-1.5">
                  <button
                    type="button"
                    onClick={() => setIsPrivateMode((v) => !v)}
                    aria-label="Toggle Private Mode"
                    className={`min-h-[36px] min-w-[36px] rounded-xl border flex items-center justify-center transition-colors ${
                      isPrivateMode
                        ? 'bg-violet-500/20 border-violet-400/50 text-violet-300'
                        : `${cardBg} ${mutedText}`
                    }`}
                  >
                    <EyeOff className="w-4 h-4" />
                  </button>
                  <button
                    type="button"
                    onClick={() => setTheme((t) => (t === 'obsidian' ? 'daylight' : 'obsidian'))}
                    aria-label="Toggle Browser Theme"
                    className={`min-h-[36px] min-w-[36px] rounded-xl border flex items-center justify-center transition-colors ${cardBg} ${mutedText}`}
                  >
                    {isDark ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              {/* Speed Dial Grid */}
              <div>
                <div className="flex items-center justify-between mb-3">
                  <span className={`text-xs font-medium ${mutedText}`}>Speed Dial</span>
                  <span className={`text-[11px] capitalize ${mutedText}`}>{tileShape} tiles</span>
                </div>
                <div className="grid grid-cols-3 gap-3.5">
                  {SPEED_DIAL_SITES.map((site) => (
                    <button
                      key={site.id}
                      type="button"
                      onClick={() => openSiteInActiveTab(site)}
                      className="group flex flex-col items-center gap-2 focus:outline-none"
                    >
                      <div
                        className={`w-16 h-16 ${shapeRadiusClass} border flex items-center justify-center text-base font-bold transition-transform group-active:scale-95 ${cardBg}`}
                        style={{color: site.accent}}
                      >
                        {site.shortLabel}
                      </div>
                      <span className="text-[11px] font-medium truncate max-w-[76px]">
                        {site.name}
                      </span>
                    </button>
                  ))}
                </div>
              </div>

              {/* Thumb-Zone Unified Search & AI Trigger */}
              <div className={`rounded-2xl border p-3.5 space-y-2.5 ${cardBg}`}>
                <button
                  type="button"
                  onClick={() => setActiveOverlay('omnibox')}
                  className={`w-full flex items-center gap-2.5 px-3 py-2.5 rounded-xl border text-left ${
                    isDark || isPrivateMode
                      ? 'bg-[#0B0D12] border-white/10 text-slate-300'
                      : 'bg-slate-100 border-slate-200 text-slate-600'
                  }`}
                >
                  <Search className="w-4 h-4 text-[#FF2E4C] shrink-0" />
                  <span className="text-xs truncate">Search web or enter URL</span>
                </button>
                <div className="flex items-center justify-between text-[11px] px-1">
                  <span className={mutedText}>
                    AdBlock: {adBlockEnabled ? 'Active' : 'Paused'}
                  </span>
                  <button
                    type="button"
                    onClick={() => setActiveOverlay('ai')}
                    className="font-medium text-[#FF2E4C] hover:underline"
                  >
                    Open SAB AI Sheet
                  </button>
                </div>
              </div>
            </div>
          ) : (
            /* ACTIVE SIMULATED GECKOVIEW PAGE */
            <article className={`space-y-4 ${desktopMode ? 'text-xs' : 'text-sm'}`}>
              <div className={`rounded-2xl border p-4 space-y-3 ${cardBg}`}>
                <div className="flex items-center justify-between text-[11px] text-slate-400">
                  <span>{activeSite.island} Island</span>
                  <span>{readerMode ? 'Reader View' : desktopMode ? 'Desktop UA' : 'Mobile View'}</span>
                </div>
                <h2 className="text-lg font-bold leading-snug">{activeSite.title}</h2>
                <div className="space-y-3 leading-relaxed opacity-90">
                  {activeSite.bodyParagraphs.map((para, idx) => (
                    <p key={idx}>{para}</p>
                  ))}
                </div>
              </div>

              <div className={`rounded-2xl border p-4 flex items-center justify-between ${cardBg}`}>
                <div>
                  <div className="text-xs font-semibold">Contextual AI Ready</div>
                  <div className={`text-[11px] ${mutedText}`}>
                    sab-bridge extracted page text for SAB AI
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => setActiveOverlay('ai')}
                  className="px-3 py-2 rounded-xl bg-[#FF2E4C] text-white text-xs font-semibold shrink-0"
                >
                  Summarize
                </button>
              </div>
            </article>
          )}
        </div>

        {/* PERSISTENT 5-SLOT OPERA MOBILE BOTTOM BAR */}
        <nav
          aria-label="SAB Browser Bottom Navigation"
          className={`absolute bottom-0 left-0 right-0 z-20 border-t backdrop-blur-md px-3 py-2 flex items-center justify-between ${barBg}`}
        >
          {/* 1. Back / Home */}
          <button
            type="button"
            onClick={activeSite ? handleGoHome : () => setActiveOverlay('omnibox')}
            aria-label={activeSite ? 'Back to Speed Dial' : 'Open Search'}
            className="min-w-[44px] min-h-[44px] flex items-center justify-center rounded-xl hover:bg-white/5 transition-colors"
          >
            {activeSite ? <ArrowLeft className="w-5 h-5" /> : <Home className="w-5 h-5" />}
          </button>

          {/* 2. Search / Omnibox */}
          <button
            type="button"
            onClick={() => setActiveOverlay('omnibox')}
            aria-label="Search or URL"
            className="min-w-[44px] min-h-[44px] flex items-center justify-center rounded-xl hover:bg-white/5 transition-colors"
          >
            <Search className="w-5 h-5" />
          </button>

          {/* 3. Central SAB AI Orb (Opera Aria placement) */}
          <button
            type="button"
            onClick={() => setActiveOverlay(activeOverlay === 'ai' ? 'none' : 'ai')}
            aria-label="Open SAB AI Assistant"
            className="w-11 h-11 rounded-full bg-[#FF2E4C] text-white flex items-center justify-center shadow-lg shadow-[#FF2E4C]/30 active:scale-95 transition-transform"
          >
            <Sparkles className="w-5 h-5" />
          </button>

          {/* 4. Tabs Gallery Counter */}
          <button
            type="button"
            onClick={() => setActiveOverlay(activeOverlay === 'tabs' ? 'none' : 'tabs')}
            aria-label="Open Tab Gallery"
            className="min-w-[44px] min-h-[44px] flex items-center justify-center rounded-xl hover:bg-white/5 transition-colors"
          >
            <span className="w-6 h-6 rounded-md border-2 border-current flex items-center justify-center text-[11px] font-bold tabular-nums">
              {visibleTabs.length}
            </span>
          </button>

          {/* 5. Opera-Style Menu Sheet */}
          <button
            type="button"
            onClick={() => setActiveOverlay(activeOverlay === 'menu' ? 'none' : 'menu')}
            aria-label="Open Browser Menu"
            className="min-w-[44px] min-h-[44px] flex items-center justify-center rounded-xl hover:bg-white/5 transition-colors"
          >
            <Menu className="w-5 h-5" />
          </button>
        </nav>

        {/* OVERLAY 1: OMNIBOX */}
        <AnimatePresence>
          {activeOverlay === 'omnibox' && (
            <motion.div
              initial={{opacity: 0, y: 20}}
              animate={{opacity: 1, y: 0}}
              exit={{opacity: 0, y: 20}}
              transition={{duration: 0.18}}
              className={`absolute inset-0 z-30 p-4 flex flex-col justify-between ${shellBg}`}
            >
              <div className="space-y-3 pt-4">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold">Search or Enter URL</span>
                  <button
                    type="button"
                    onClick={() => setActiveOverlay('none')}
                    className="p-2 rounded-lg hover:bg-white/10"
                  >
                    <X className="w-4 h-4" />
                  </button>
                </div>
                <div className="space-y-2">
                  <div className={`text-[11px] ${mutedText}`}>Quick Jump</div>
                  {SPEED_DIAL_SITES.slice(0, 4).map((site) => (
                    <button
                      key={site.id}
                      type="button"
                      onClick={() => openSiteInActiveTab(site)}
                      className={`w-full flex items-center justify-between px-3 py-2.5 rounded-xl border text-left ${cardBg}`}
                    >
                      <span className="text-xs font-medium">{site.name}</span>
                      <span className={`text-[11px] ${mutedText}`}>{site.domain}</span>
                    </button>
                  ))}
                </div>
              </div>

              <form onSubmit={handleOmniboxSubmit} className="pb-4 space-y-2">
                <div className={`flex items-center gap-2 p-2 rounded-2xl border ${cardBg}`}>
                  <Globe className="w-4 h-4 text-sky-400 ml-1 shrink-0" />
                  <input
                    type="text"
                    value={searchInput}
                    onChange={(e) => setSearchInput(e.target.value)}
                    placeholder="Type wiki, github, mdn, or arxiv..."
                    autoFocus
                    className="flex-1 bg-transparent text-xs focus:outline-none"
                  />
                  <button
                    type="submit"
                    className="px-3 py-1.5 rounded-xl bg-[#FF2E4C] text-white text-xs font-semibold"
                  >
                    Go
                  </button>
                </div>
              </form>
            </motion.div>
          )}
        </AnimatePresence>

        {/* OVERLAY 2: TAB GALLERY & TAB ISLANDS */}
        <AnimatePresence>
          {activeOverlay === 'tabs' && (
            <motion.div
              initial={{opacity: 0, y: 24}}
              animate={{opacity: 1, y: 0}}
              exit={{opacity: 0, y: 24}}
              transition={{duration: 0.2}}
              className={`absolute inset-0 z-30 flex flex-col p-4 pb-20 ${shellBg}`}
            >
              <div className="flex items-center justify-between pt-2 pb-4">
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => setIsPrivateMode(false)}
                    className={`px-3 py-1.5 rounded-lg text-xs font-medium ${
                      !isPrivateMode ? 'bg-[#FF2E4C] text-white' : `${cardBg} ${mutedText}`
                    }`}
                  >
                    Normal
                  </button>
                  <button
                    type="button"
                    onClick={() => setIsPrivateMode(true)}
                    className={`px-3 py-1.5 rounded-lg text-xs font-medium ${
                      isPrivateMode ? 'bg-violet-600 text-white' : `${cardBg} ${mutedText}`
                    }`}
                  >
                    Private
                  </button>
                </div>
                <button
                  type="button"
                  onClick={() => setActiveOverlay('none')}
                  className="p-2 rounded-lg hover:bg-white/10"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              <div className="flex-1 overflow-y-auto space-y-2.5">
                {visibleTabs.map((tab) => {
                  const isCurrent = tab.id === activeTab?.id;
                  return (
                    <div
                      key={tab.id}
                      className={`rounded-2xl border p-3.5 flex items-center justify-between gap-3 ${
                        isCurrent ? 'border-[#FF2E4C]/70 bg-[#FF2E4C]/10' : cardBg
                      }`}
                    >
                      <button
                        type="button"
                        onClick={() => {
                          setActiveTabId(tab.id);
                          setActiveOverlay('none');
                        }}
                        className="flex-1 text-left min-w-0"
                      >
                        <div className="text-[11px] text-sky-400 font-medium">
                          {tab.island} Island
                        </div>
                        <div className="text-xs font-semibold truncate">{tab.title}</div>
                        <div className={`text-[11px] truncate ${mutedText}`}>{tab.url}</div>
                      </button>
                      <button
                        type="button"
                        onClick={() => handleCloseTab(tab.id)}
                        aria-label="Close tab"
                        className="p-2 rounded-lg hover:bg-white/10 shrink-0"
                      >
                        <X className="w-4 h-4" />
                      </button>
                    </div>
                  );
                })}
              </div>

              <button
                type="button"
                onClick={() => handleCreateTab(isPrivateMode)}
                className="mt-3 w-full py-3 rounded-2xl bg-[#FF2E4C] text-white text-xs font-semibold flex items-center justify-center gap-2"
              >
                <Plus className="w-4 h-4" />
                New {isPrivateMode ? 'Private ' : ''}Tab
              </button>
            </motion.div>
          )}
        </AnimatePresence>

        {/* OVERLAY 3: OPERA-STYLE BOTTOM SHEET MENU */}
        <AnimatePresence>
          {activeOverlay === 'menu' && (
            <motion.div
              initial={{opacity: 0}}
              animate={{opacity: 1}}
              exit={{opacity: 0}}
              className="absolute inset-0 z-30 bg-black/55 backdrop-blur-xs flex items-end"
              onClick={() => setActiveOverlay('none')}
            >
              <motion.div
                initial={{y: '100%'}}
                animate={{y: 0}}
                exit={{y: '100%'}}
                transition={{duration: 0.22, ease: [0.16, 1, 0.3, 1]}}
                onClick={(e) => e.stopPropagation()}
                className={`w-full rounded-t-3xl border-t p-5 pb-20 space-y-5 ${
                  isDark || isPrivateMode ? 'bg-[#171C26] border-white/10' : 'bg-white border-slate-200'
                }`}
              >
                <div className="w-10 h-1 rounded-full bg-slate-500/40 mx-auto" />

                <div className="grid grid-cols-2 gap-2.5">
                  <button
                    type="button"
                    onClick={() => setAdBlockEnabled((v) => !v)}
                    className={`p-3 rounded-2xl border text-left space-y-1 ${
                      adBlockEnabled ? 'border-emerald-500/40 bg-emerald-500/10' : cardBg
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <Shield className="w-4 h-4 text-emerald-400" />
                      {adBlockEnabled && <Check className="w-3.5 h-3.5 text-emerald-400" />}
                    </div>
                    <div className="text-xs font-semibold">Ad & Tracker Block</div>
                  </button>

                  <button
                    type="button"
                    onClick={() => setCookieBlockerEnabled((v) => !v)}
                    className={`p-3 rounded-2xl border text-left space-y-1 ${
                      cookieBlockerEnabled ? 'border-sky-500/40 bg-sky-500/10' : cardBg
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <Lock className="w-4 h-4 text-sky-400" />
                      {cookieBlockerEnabled && <Check className="w-3.5 h-3.5 text-sky-400" />}
                    </div>
                    <div className="text-xs font-semibold">Cookie Dialog Block</div>
                  </button>

                  <button
                    type="button"
                    onClick={() => setReaderMode((v) => !v)}
                    className={`p-3 rounded-2xl border text-left space-y-1 ${
                      readerMode ? 'border-[#FF2E4C]/50 bg-[#FF2E4C]/10' : cardBg
                    }`}
                  >
                    <BookOpen className="w-4 h-4 text-[#FF2E4C]" />
                    <div className="text-xs font-semibold">Reader Mode</div>
                  </button>

                  <button
                    type="button"
                    onClick={() => setDesktopMode((v) => !v)}
                    className={`p-3 rounded-2xl border text-left space-y-1 ${
                      desktopMode ? 'border-amber-400/50 bg-amber-500/10' : cardBg
                    }`}
                  >
                    <Monitor className="w-4 h-4 text-amber-400" />
                    <div className="text-xs font-semibold">Desktop Site</div>
                  </button>
                </div>

                {/* Speed Dial Shape Selector */}
                <div className="space-y-2">
                  <div className={`text-xs font-medium ${mutedText}`}>Speed Dial Tile Shape</div>
                  <div className="grid grid-cols-3 gap-2">
                    {(['squircle', 'circle', 'square'] as TileShape[]).map((shape) => (
                      <button
                        key={shape}
                        type="button"
                        onClick={() => setTileShape(shape)}
                        className={`py-2 rounded-xl text-xs font-medium capitalize border ${
                          tileShape === shape
                            ? 'bg-[#FF2E4C] text-white border-[#FF2E4C]'
                            : cardBg
                        }`}
                      >
                        {shape}
                      </button>
                    ))}
                  </div>
                </div>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* OVERLAY 4: CONTEXTUAL SAB AI SHEET PREVIEW */}
        <AnimatePresence>
          {activeOverlay === 'ai' && (
            <motion.div
              initial={{opacity: 0}}
              animate={{opacity: 1}}
              exit={{opacity: 0}}
              className="absolute inset-0 z-30 bg-black/60 backdrop-blur-xs flex items-end"
              onClick={() => setActiveOverlay('none')}
            >
              <motion.div
                initial={{y: '100%'}}
                animate={{y: 0}}
                exit={{y: '100%'}}
                transition={{duration: 0.22, ease: [0.16, 1, 0.3, 1]}}
                onClick={(e) => e.stopPropagation()}
                className={`w-full rounded-t-3xl border-t p-5 pb-20 space-y-4 ${
                  isDark || isPrivateMode ? 'bg-[#171C26] border-white/10' : 'bg-white border-slate-200'
                }`}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Sparkles className="w-4 h-4 text-[#FF2E4C]" />
                    <span className="text-sm font-semibold">SAB AI Context Sheet</span>
                  </div>
                  <button
                    type="button"
                    onClick={() => setActiveOverlay('none')}
                    className="p-1.5 rounded-lg hover:bg-white/10"
                  >
                    <X className="w-4 h-4" />
                  </button>
                </div>

                <div className={`rounded-2xl border p-3.5 space-y-2 ${cardBg}`}>
                  <div className={`text-[11px] ${mutedText}`}>
                    {activeSite
                      ? `Context from ${activeSite.domain} (via sab-bridge)`
                      : 'Start Page Mode · Open any Speed Dial tile for page summary'}
                  </div>
                  <ul className="space-y-1.5 text-xs leading-relaxed">
                    {(activeSite
                      ? activeSite.summaryPoints
                      : [
                          'Opera Mobile bottom-bar ergonomics mapped to GeckoView 159+.',
                          'Strictly arm64-v8a non-omni AAR targeting Android 10+ (minSdk 29).',
                          'Click any Speed Dial site to preview contextual extraction.',
                        ]
                    ).map((point, i) => (
                      <li key={i} className="flex items-start gap-2">
                        <span className="text-[#FF2E4C] font-bold">•</span>
                        <span>{point}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* IN-PHONE SPLASH ANIMATION OVERLAY */}
        <AnimatePresence>
          {showSplash && (
            <SplashOverlay compact onDismiss={() => setShowSplash(false)} />
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}
