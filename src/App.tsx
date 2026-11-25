/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import {useEffect, useState} from 'react';
import {AnimatePresence} from 'motion/react';
import {
  ArrowLeft,
  ArrowRight,
  Bookmark,
  ExternalLink,
  EyeOff,
  Home,
  Lock,
  Menu,
  Moon,
  Plus,
  RefreshCw,
  Search,
  Shield,
  Sparkles,
  Sun,
  X,
} from 'lucide-react';
import {
  LibraryOverlay,
  MenuSheetOverlay,
  OmniboxOverlay,
  TabGalleryOverlay,
} from './components/BrowserOverlays';
import {SabAiSheet} from './components/SabAiSheet';
import {SabLogo} from './components/SabLogo';
import {SplashOverlay} from './components/SplashOverlay';
import {WebPageView} from './components/WebPageView';
import {
  DEFAULT_SPEED_DIAL,
  type AiChatMessage,
  type BookmarkItem,
  type BrowserSettings,
  type BrowserTab,
  type HistoryEntry,
  type SpeedDialItem,
} from './types/browser';

const STORAGE_KEYS = {
  settings: 'sab_settings_v1',
  speedDial: 'sab_speed_dial_v1',
  bookmarks: 'sab_bookmarks_v1',
  history: 'sab_history_v1',
};

function loadJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

function looksLikeUrl(input: string): boolean {
  const trimmed = input.trim();
  if (!trimmed || trimmed.includes(' ')) return false;
  if (/^https?:\/\//i.test(trimmed)) return true;
  return /^[a-zA-Z0-9-]+(\.[a-zA-Z0-9-]+)+(:\d+)?(\/.*)?$/.test(trimmed);
}

function createInitialTab(isPrivate = false): BrowserTab {
  return {
    id: `tab-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
    title: isPrivate ? 'Private Start' : 'Speed Dial',
    url: 'sab://start',
    island: isPrivate ? 'Private' : 'Start',
    isPrivate,
    historyStack: ['sab://start'],
    historyIndex: 0,
    isLoading: false,
    error: null,
    pageData: null,
    searchData: null,
    blockedCount: 0,
  };
}

export default function App() {
  const [showSplash, setShowSplash] = useState(true);
  const [splashRunId, setSplashRunId] = useState(1);

  const [settings, setSettings] = useState<BrowserSettings>(() =>
    loadJson<BrowserSettings>(STORAGE_KEYS.settings, {
      theme: 'obsidian',
      tileShape: 'squircle',
      adBlockEnabled: true,
      readerMode: false,
      desktopMode: false,
    }),
  );

  const [speedDial, setSpeedDial] = useState<SpeedDialItem[]>(() =>
    loadJson<SpeedDialItem[]>(STORAGE_KEYS.speedDial, DEFAULT_SPEED_DIAL),
  );
  const [bookmarks, setBookmarks] = useState<BookmarkItem[]>(() =>
    loadJson<BookmarkItem[]>(STORAGE_KEYS.bookmarks, []),
  );
  const [history, setHistory] = useState<HistoryEntry[]>(() =>
    loadJson<HistoryEntry[]>(STORAGE_KEYS.history, []),
  );

  const [tabs, setTabs] = useState<BrowserTab[]>(() => [createInitialTab(false)]);
  const [activeTabId, setActiveTabId] = useState<string>(() => tabs[0].id);
  const [isPrivateMode, setIsPrivateMode] = useState(false);

  const [omniboxOpen, setOmniboxOpen] = useState(false);
  const [tabsOpen, setTabsOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [aiOpen, setAiOpen] = useState(false);
  const [libraryOpen, setLibraryOpen] = useState(false);
  const [addDialOpen, setAddDialOpen] = useState(false);
  const [newDialName, setNewDialName] = useState('');
  const [newDialUrl, setNewDialUrl] = useState('');
  const [startBarInput, setStartBarInput] = useState('');

  const [findBarOpen, setFindBarOpen] = useState(false);
  const [findQuery, setFindQuery] = useState('');

  const [aiMessages, setAiMessages] = useState<AiChatMessage[]>([]);
  const [aiThinking, setAiThinking] = useState(false);

  useEffect(() => {
    setShowSplash(true);
    const timer = window.setTimeout(() => setShowSplash(false), 1100);
    return () => window.clearTimeout(timer);
  }, [splashRunId]);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEYS.settings, JSON.stringify(settings));
    } catch {
      // ignore storage quota errors
    }
  }, [settings]);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEYS.speedDial, JSON.stringify(speedDial));
    } catch {
      // ignore
    }
  }, [speedDial]);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEYS.bookmarks, JSON.stringify(bookmarks));
    } catch {
      // ignore
    }
  }, [bookmarks]);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEYS.history, JSON.stringify(history));
    } catch {
      // ignore
    }
  }, [history]);

  const visibleTabs = tabs.filter((t) => t.isPrivate === isPrivateMode);
  const activeTab =
    tabs.find((t) => t.id === activeTabId && t.isPrivate === isPrivateMode) ||
    visibleTabs[0] ||
    tabs[0];

  const updateTab = (tabId: string, updater: (t: BrowserTab) => BrowserTab) => {
    setTabs((prev) => prev.map((t) => (t.id === tabId ? updater(t) : t)));
  };

  const recordHistory = (title: string, url: string, isPrivate: boolean) => {
    if (isPrivate || url.startsWith('sab://')) return;
    let domain = url;
    try {
      domain = new URL(url).hostname;
    } catch {
      // keep raw
    }
    setHistory((prev) => [
      {
        id: `hist-${Date.now()}-${Math.random().toString(36).slice(2, 5)}`,
        title,
        url,
        domain,
        visitedAt: Date.now(),
      },
      ...prev.filter((h) => h.url !== url).slice(0, 79),
    ]);
  };

  const loadTargetIntoTab = async (
    tabId: string,
    target: string,
    pushHistory = true,
    overrideSettings?: BrowserSettings,
  ) => {
    const activeSettings = overrideSettings || settings;

    if (target === 'sab://start') {
      updateTab(tabId, (tab) => {
        const nextStack = pushHistory
          ? [...tab.historyStack.slice(0, tab.historyIndex + 1), 'sab://start']
          : tab.historyStack;
        return {
          ...tab,
          title: tab.isPrivate ? 'Private Start' : 'Speed Dial',
          url: 'sab://start',
          island: tab.isPrivate ? 'Private' : 'Start',
          historyStack: nextStack,
          historyIndex: pushHistory ? nextStack.length - 1 : tab.historyIndex,
          isLoading: false,
          error: null,
          pageData: null,
          searchData: null,
          blockedCount: 0,
        };
      });
      return;
    }

    if (target.startsWith('sab://search?q=')) {
      const query = decodeURIComponent(target.slice('sab://search?q='.length));
      updateTab(tabId, (tab) => {
        const nextStack = pushHistory
          ? [...tab.historyStack.slice(0, tab.historyIndex + 1), target]
          : tab.historyStack;
        return {
          ...tab,
          title: `Search: ${query}`,
          url: target,
          island: 'Search',
          historyStack: nextStack,
          historyIndex: pushHistory ? nextStack.length - 1 : tab.historyIndex,
          isLoading: true,
          error: null,
          pageData: null,
        };
      });

      try {
        const res = await fetch('/api/search', {
          method: 'POST',
          headers: {'Content-Type': 'application/json'},
          body: JSON.stringify({query}),
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Search failed.');
        updateTab(tabId, (tab) => ({
          ...tab,
          isLoading: false,
          searchData: {query, results: data.results || []},
          error: null,
        }));
      } catch (err) {
        updateTab(tabId, (tab) => ({
          ...tab,
          isLoading: false,
          error: err instanceof Error ? err.message : 'Search failed.',
        }));
      }
      return;
    }

    const normalizedUrl = /^https?:\/\//i.test(target) ? target : `https://${target}`;
    let domain = normalizedUrl;
    try {
      domain = new URL(normalizedUrl).hostname;
    } catch {
      // keep raw
    }

    updateTab(tabId, (tab) => {
      const nextStack = pushHistory
        ? [...tab.historyStack.slice(0, tab.historyIndex + 1), normalizedUrl]
        : tab.historyStack;
      return {
        ...tab,
        title: domain,
        url: normalizedUrl,
        island: domain.replace(/^www\./, ''),
        historyStack: nextStack,
        historyIndex: pushHistory ? nextStack.length - 1 : tab.historyIndex,
        isLoading: true,
        error: null,
        searchData: null,
      };
    });

    try {
      const res = await fetch('/api/browse', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({
          url: normalizedUrl,
          adBlockEnabled: activeSettings.adBlockEnabled,
          desktopMode: activeSettings.desktopMode,
        }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Failed to load webpage.');

      let isPriv = false;
      updateTab(tabId, (tab) => {
        isPriv = tab.isPrivate;
        return {
          ...tab,
          title: data.title || domain,
          url: data.finalUrl || normalizedUrl,
          island: (data.domain || domain).replace(/^www\./, ''),
          isLoading: false,
          error: null,
          pageData: data,
          blockedCount: data.blockedCount || 0,
        };
      });
      recordHistory(data.title || domain, data.finalUrl || normalizedUrl, isPriv);
    } catch (err) {
      updateTab(tabId, (tab) => ({
        ...tab,
        isLoading: false,
        error: err instanceof Error ? err.message : 'Unable to fetch webpage.',
      }));
    }
  };

  const handleNavigateOrSearch = (input: string) => {
    const trimmed = input.trim();
    if (!trimmed) return;
    if (trimmed.startsWith('sab://')) {
      loadTargetIntoTab(activeTab.id, trimmed, true);
      return;
    }
    if (looksLikeUrl(trimmed)) {
      loadTargetIntoTab(activeTab.id, trimmed, true);
    } else {
      loadTargetIntoTab(activeTab.id, `sab://search?q=${encodeURIComponent(trimmed)}`, true);
    }
  };

  const canGoBack = activeTab.historyIndex > 0;
  const canGoForward = activeTab.historyIndex < activeTab.historyStack.length - 1;

  const handleBack = () => {
    if (!canGoBack) return;
    const prevIdx = activeTab.historyIndex - 1;
    const targetUrl = activeTab.historyStack[prevIdx];
    updateTab(activeTab.id, (tab) => ({...tab, historyIndex: prevIdx}));
    loadTargetIntoTab(activeTab.id, targetUrl, false);
  };

  const handleForward = () => {
    if (!canGoForward) return;
    const nextIdx = activeTab.historyIndex + 1;
    const targetUrl = activeTab.historyStack[nextIdx];
    updateTab(activeTab.id, (tab) => ({...tab, historyIndex: nextIdx}));
    loadTargetIntoTab(activeTab.id, targetUrl, false);
  };

  const handleCreateTab = (privateFlag: boolean) => {
    const tab = createInitialTab(privateFlag);
    setIsPrivateMode(privateFlag);
    setTabs((prev) => [...prev, tab]);
    setActiveTabId(tab.id);
  };

  const handleCloseTab = (id: string) => {
    setTabs((prev) => {
      const remaining = prev.filter((t) => t.id !== id);
      if (remaining.length === 0) {
        const fresh = createInitialTab(isPrivateMode);
        setActiveTabId(fresh.id);
        return [fresh];
      }
      if (activeTabId === id) {
        const match = remaining.find((t) => t.isPrivate === isPrivateMode) || remaining[0];
        setActiveTabId(match.id);
        setIsPrivateMode(match.isPrivate);
      }
      return remaining;
    });
  };

  const handleClearAllTabs = (privateFlag: boolean) => {
    const fresh = createInitialTab(privateFlag);
    setTabs((prev) => [...prev.filter((t) => t.isPrivate !== privateFlag), fresh]);
    setActiveTabId(fresh.id);
  };

  const handleTogglePrivateMode = () => {
    const nextPrivate = !isPrivateMode;
    setIsPrivateMode(nextPrivate);
    const existing = tabs.find((t) => t.isPrivate === nextPrivate);
    if (existing) {
      setActiveTabId(existing.id);
    } else {
      const fresh = createInitialTab(nextPrivate);
      setTabs((prev) => [...prev, fresh]);
      setActiveTabId(fresh.id);
    }
  };

  const handleSendAiPrompt = async (prompt: string) => {
    setAiOpen(true);
    const userMsg: AiChatMessage = {
      id: `u-${Date.now()}`,
      role: 'user',
      text: prompt,
    };
    setAiMessages((prev) => [...prev, userMsg]);
    setAiThinking(true);

    try {
      const res = await fetch('/api/ai', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({
          prompt,
          useWebGrounding: !activeTab.pageData,
          pageContext: activeTab.pageData
            ? {
                url: activeTab.pageData.finalUrl,
                title: activeTab.pageData.title,
                textContent: activeTab.pageData.textContent,
              }
            : undefined,
        }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'SAB AI request failed.');

      setAiMessages((prev) => [
        ...prev,
        {
          id: `a-${Date.now()}`,
          role: 'assistant',
          text: data.text,
          sources: data.sources,
        },
      ]);
    } catch (err) {
      setAiMessages((prev) => [
        ...prev,
        {
          id: `e-${Date.now()}`,
          role: 'error',
          text: err instanceof Error ? err.message : 'Unable to connect to Gemini AI.',
        },
      ]);
    } finally {
      setAiThinking(false);
    }
  };

  const isCurrentBookmarked = Boolean(
    activeTab.pageData && bookmarks.some((b) => b.url === activeTab.pageData!.finalUrl),
  );

  const handleToggleBookmark = () => {
    if (!activeTab.pageData) return;
    const targetUrl = activeTab.pageData.finalUrl;
    if (isCurrentBookmarked) {
      setBookmarks((prev) => prev.filter((b) => b.url !== targetUrl));
    } else {
      setBookmarks((prev) => [
        {
          id: `bm-${Date.now()}`,
          title: activeTab.pageData!.title,
          url: targetUrl,
          domain: activeTab.pageData!.domain,
          addedAt: Date.now(),
        },
        ...prev,
      ]);
    }
  };

  const handleDownloadPageText = () => {
    if (!activeTab.pageData) return;
    const content = `${activeTab.pageData.title}\n${activeTab.pageData.finalUrl}\n\n${activeTab.pageData.textContent}`;
    const blob = new Blob([content], {type: 'text/plain;charset=utf-8'});
    const objectUrl = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = objectUrl;
    a.download = `${activeTab.pageData.domain.replace(/[^a-z0-9.-]/gi, '_')}.txt`;
    a.click();
    URL.revokeObjectURL(objectUrl);
  };

  const handleAddSpeedDial = (e: React.FormEvent) => {
    e.preventDefault();
    const name = newDialName.trim();
    const rawUrl = newDialUrl.trim();
    if (!name || !rawUrl) return;
    const normalized = /^https?:\/\//i.test(rawUrl) ? rawUrl : `https://${rawUrl}`;
    const shortLabel = name.slice(0, 2).toUpperCase();
    setSpeedDial((prev) => [
      ...prev,
      {
        id: `sd-${Date.now()}`,
        name,
        url: normalized,
        shortLabel,
        accent: '#38BDF8',
      },
    ]);
    setNewDialName('');
    setNewDialUrl('');
    setAddDialOpen(false);
  };

  const handleUpdateSettings = (patch: Partial<BrowserSettings>) => {
    const next = {...settings, ...patch};
    setSettings(next);
    if (
      activeTab.url.startsWith('http') &&
      (patch.adBlockEnabled !== undefined || patch.desktopMode !== undefined)
    ) {
      loadTargetIntoTab(activeTab.id, activeTab.url, false, next);
    }
  };

  const isLight = settings.theme === 'daylight';
  const canvasClass = isPrivateMode
    ? 'bg-[#110D1B] text-slate-100'
    : settings.theme === 'midnight'
      ? 'bg-black text-slate-100'
      : isLight
        ? 'bg-[#F4F6F9] text-slate-900'
        : 'bg-[#0B0D12] text-slate-100';
  const chromeClass = isPrivateMode
    ? 'bg-[#1A1429]/95 border-violet-500/20'
    : isLight
      ? 'bg-white/95 border-slate-200'
      : 'bg-[#141821]/95 border-white/10';
  const cardClass = isLight
    ? 'bg-white border-slate-200 text-slate-900'
    : 'bg-[#141821] border-white/10 text-slate-100';

  const tileShapeClass =
    settings.tileShape === 'circle'
      ? 'rounded-full'
      : settings.tileShape === 'square'
        ? 'rounded-xl'
        : 'rounded-[22px]';

  return (
    <div className={`min-h-screen flex flex-col ${canvasClass}`}>
      {/* COMPACT OPERA-STYLE TOP ADDRESS CHROME */}
      <header className={`sticky top-0 z-30 border-b backdrop-blur-md ${chromeClass}`}>
        <div className="mx-auto max-w-6xl px-3 sm:px-5 h-14 flex items-center gap-2.5">
          {/* Desktop Navigation Helpers */}
          <div className="hidden sm:flex items-center gap-1 shrink-0">
            <button
              type="button"
              onClick={handleBack}
              disabled={!canGoBack}
              aria-label="Back"
              className="w-9 h-9 rounded-xl flex items-center justify-center hover:bg-white/10 disabled:opacity-30 transition-colors"
            >
              <ArrowLeft className="w-4 h-4" />
            </button>
            <button
              type="button"
              onClick={handleForward}
              disabled={!canGoForward}
              aria-label="Forward"
              className="w-9 h-9 rounded-xl flex items-center justify-center hover:bg-white/10 disabled:opacity-30 transition-colors"
            >
              <ArrowRight className="w-4 h-4" />
            </button>
          </div>

          {/* Unified Address Pill Trigger */}
          <button
            type="button"
            onClick={() => setOmniboxOpen(true)}
            className={`flex-1 min-w-0 h-10 px-3.5 rounded-2xl border flex items-center justify-between gap-2 text-left transition-colors ${
              isLight
                ? 'bg-slate-100 border-slate-200 text-slate-800 hover:border-slate-300'
                : 'bg-[#0B0D12] border-white/10 text-slate-200 hover:border-white/20'
            }`}
          >
            <span className="flex items-center gap-2 min-w-0">
              {isPrivateMode ? (
                <EyeOff className="w-4 h-4 text-violet-400 shrink-0" />
              ) : activeTab.url.startsWith('https://') ? (
                <Lock className="w-3.5 h-3.5 text-sky-400 shrink-0" />
              ) : (
                <Search className="w-3.5 h-3.5 text-[#FF2E4C] shrink-0" />
              )}
              <span className="text-xs sm:text-sm font-medium truncate">
                {activeTab.url === 'sab://start'
                  ? 'Search web or enter URL'
                  : activeTab.pageData?.domain || activeTab.url}
              </span>
            </span>

            {activeTab.blockedCount > 0 && settings.adBlockEnabled && (
              <span className="inline-flex items-center gap-1 text-[11px] font-mono text-emerald-400 shrink-0 tabular-nums">
                <Shield className="w-3.5 h-3.5" />
                {activeTab.blockedCount}
              </span>
            )}
          </button>

          {/* Bookmark & Reload Actions */}
          <div className="flex items-center gap-1 shrink-0">
            {activeTab.pageData && (
              <button
                type="button"
                onClick={handleToggleBookmark}
                aria-label="Toggle Bookmark"
                className={`w-9 h-9 rounded-xl flex items-center justify-center hover:bg-white/10 transition-colors ${
                  isCurrentBookmarked ? 'text-amber-400' : 'text-slate-400'
                }`}
              >
                <Bookmark
                  className="w-4 h-4"
                  fill={isCurrentBookmarked ? 'currentColor' : 'none'}
                />
              </button>
            )}
            {activeTab.url !== 'sab://start' && (
              <button
                type="button"
                onClick={() => loadTargetIntoTab(activeTab.id, activeTab.url, false)}
                aria-label="Reload page"
                className="w-9 h-9 rounded-xl flex items-center justify-center hover:bg-white/10 transition-colors"
              >
                <RefreshCw
                  className={`w-4 h-4 ${activeTab.isLoading ? 'animate-spin text-[#FF2E4C]' : ''}`}
                />
              </button>
            )}
          </div>
        </div>

        {/* Loading Progress Line */}
        {activeTab.isLoading && (
          <div className="h-0.5 w-full bg-slate-800 overflow-hidden">
            <div className="h-full w-2/3 bg-[#FF2E4C] animate-pulse" />
          </div>
        )}

        {/* Find in Page Bar */}
        {findBarOpen && (
          <div className="mx-auto max-w-6xl px-4 py-2 border-t border-white/10 flex items-center gap-2">
            <Search className="w-4 h-4 text-amber-400 shrink-0" />
            <input
              type="text"
              value={findQuery}
              onChange={(e) => setFindQuery(e.target.value)}
              placeholder="Find text on this page..."
              autoFocus
              className="flex-1 bg-transparent text-xs focus:outline-none"
            />
            <button
              type="button"
              onClick={() => {
                setFindBarOpen(false);
                setFindQuery('');
              }}
              className="p-1.5 rounded-lg hover:bg-white/10"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        )}
      </header>

      {/* MAIN BROWSER CONTENT VIEWPORT */}
      <main className="flex-1 pb-24">
        {activeTab.url === 'sab://start' && (
          <div className="mx-auto max-w-4xl px-4 sm:px-6 py-8 sm:py-12 space-y-8">
            {/* Start Page Header */}
            <div className="flex items-center justify-between gap-4">
              <div className="flex items-center gap-3.5">
                <SabLogo size={48} className="rounded-2xl" />
                <div>
                  <h1 className="text-xl sm:text-2xl font-bold tracking-tight">
                    {isPrivateMode ? 'Private SAB Session' : 'Simple AI Browser'}
                  </h1>
                  <p className="text-xs text-slate-400">
                    SAB · Opera-Inspired Shell · Live Web &amp; Gemini AI
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={handleTogglePrivateMode}
                  aria-label="Toggle Private Mode"
                  className={`min-h-[40px] px-3 rounded-xl border text-xs font-medium inline-flex items-center gap-1.5 transition-colors ${
                    isPrivateMode
                      ? 'bg-violet-600/20 border-violet-400/50 text-violet-300'
                      : cardClass
                  }`}
                >
                  <EyeOff className="w-4 h-4" />
                  <span className="hidden sm:inline">Private</span>
                </button>
                <button
                  type="button"
                  onClick={() =>
                    handleUpdateSettings({
                      theme: isLight ? 'obsidian' : 'daylight',
                    })
                  }
                  aria-label="Toggle Theme"
                  className={`min-h-[40px] min-w-[40px] rounded-xl border flex items-center justify-center transition-colors ${cardClass}`}
                >
                  {isLight ? <Moon className="w-4 h-4" /> : <Sun className="w-4 h-4" />}
                </button>
              </div>
            </div>

            {/* Unified Search / URL / Ask AI Input */}
            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (!startBarInput.trim()) return;
                handleNavigateOrSearch(startBarInput);
                setStartBarInput('');
              }}
              className={`rounded-3xl border p-3 sm:p-4 space-y-3 ${cardClass}`}
            >
              <div
                className={`flex items-center gap-3 px-4 py-3 rounded-2xl border ${
                  isLight ? 'bg-slate-50 border-slate-200' : 'bg-[#0B0D12] border-white/10'
                }`}
              >
                <Search className="w-4 h-4 text-[#FF2E4C] shrink-0" />
                <input
                  type="text"
                  value={startBarInput}
                  onChange={(e) => setStartBarInput(e.target.value)}
                  placeholder="Search the web or type any URL (e.g. wikipedia.org)..."
                  className="flex-1 bg-transparent text-sm focus:outline-none"
                />
                <button
                  type="submit"
                  className="px-4 py-2 rounded-xl bg-[#FF2E4C] text-white text-xs font-semibold hover:bg-[#e62440] transition-colors shrink-0"
                >
                  Go
                </button>
              </div>
              <div className="flex flex-wrap items-center justify-between gap-2 px-1 text-xs text-slate-400">
                <span>
                  Ad &amp; Tracker Blocker: {settings.adBlockEnabled ? 'On' : 'Paused'}
                </span>
                <button
                  type="button"
                  onClick={() => {
                    if (startBarInput.trim()) {
                      handleSendAiPrompt(startBarInput.trim());
                      setStartBarInput('');
                    } else {
                      setAiOpen(true);
                    }
                  }}
                  className="inline-flex items-center gap-1.5 font-semibold text-[#FF2E4C] hover:underline"
                >
                  <Sparkles className="w-3.5 h-3.5" />
                  Ask SAB AI Instead
                </button>
              </div>
            </form>

            {/* Speed Dial Grid */}
            <section className="space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-semibold text-slate-400">Speed Dial</h2>
                <button
                  type="button"
                  onClick={() => setAddDialOpen(true)}
                  className="inline-flex items-center gap-1.5 text-xs font-medium text-sky-400 hover:underline"
                >
                  <Plus className="w-3.5 h-3.5" />
                  Add Site
                </button>
              </div>

              <div className="grid grid-cols-3 sm:grid-cols-4 gap-4 sm:gap-5">
                {speedDial.map((site) => (
                  <div key={site.id} className="relative group flex flex-col items-center gap-2">
                    <button
                      type="button"
                      onClick={() => loadTargetIntoTab(activeTab.id, site.url, true)}
                      className={`w-16 h-16 sm:w-20 sm:h-20 ${tileShapeClass} border flex items-center justify-center text-lg font-bold transition-transform hover:scale-105 active:scale-95 ${cardClass}`}
                      style={{color: site.accent}}
                    >
                      {site.shortLabel}
                    </button>
                    <span className="text-xs font-medium truncate max-w-[96px]">
                      {site.name}
                    </span>
                    <button
                      type="button"
                      aria-label={`Remove ${site.name}`}
                      onClick={() =>
                        setSpeedDial((prev) => prev.filter((item) => item.id !== site.id))
                      }
                      className="opacity-0 group-hover:opacity-100 absolute -top-1 right-2 sm:right-4 w-5 h-5 rounded-full bg-slate-800 border border-white/20 text-white flex items-center justify-center transition-opacity"
                    >
                      <X className="w-3 h-3" />
                    </button>
                  </div>
                ))}
              </div>
            </section>
          </div>
        )}

        {/* LIVE WEB SEARCH RESULTS */}
        {activeTab.url.startsWith('sab://search') && (
          <div className="mx-auto max-w-3xl px-4 sm:px-6 py-6 space-y-5">
            <div className={`rounded-2xl border p-4 flex flex-wrap items-center justify-between gap-3 ${cardClass}`}>
              <div>
                <div className="text-xs text-slate-400">Web Search Results</div>
                <h2 className="text-base font-semibold">
                  {activeTab.searchData?.query || activeTab.title}
                </h2>
              </div>
              <button
                type="button"
                onClick={() =>
                  handleSendAiPrompt(
                    activeTab.searchData?.query || activeTab.title.replace('Search: ', ''),
                  )
                }
                className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-[#FF2E4C] text-white text-xs font-semibold"
              >
                <Sparkles className="w-3.5 h-3.5" />
                AI Overview
              </button>
            </div>

            {activeTab.isLoading ? (
              <div className="space-y-3">
                {[1, 2, 3, 4].map((n) => (
                  <div
                    key={n}
                    className={`rounded-2xl border p-5 space-y-2 animate-pulse ${cardClass}`}
                  >
                    <div className="h-3 w-36 bg-slate-700/40 rounded" />
                    <div className="h-5 w-3/4 bg-slate-700/40 rounded" />
                    <div className="h-4 w-full bg-slate-700/30 rounded" />
                  </div>
                ))}
              </div>
            ) : activeTab.error ? (
              <div className={`rounded-2xl border p-6 space-y-3 ${cardClass}`}>
                <div className="text-sm font-semibold text-[#FF2E4C]">
                  Search Error: {activeTab.error}
                </div>
                <button
                  type="button"
                  onClick={() =>
                    handleSendAiPrompt(
                      activeTab.searchData?.query || activeTab.title.replace('Search: ', ''),
                    )
                  }
                  className="px-4 py-2 rounded-xl bg-[#FF2E4C] text-white text-xs font-semibold"
                >
                  Ask SAB AI with Web Search Instead
                </button>
              </div>
            ) : (
              <div className="space-y-3">
                {(activeTab.searchData?.results || []).length === 0 ? (
                  <div className={`rounded-2xl border p-6 text-sm text-slate-400 ${cardClass}`}>
                    No direct web results returned. Try SAB AI Overview above or enter a direct URL.
                  </div>
                ) : (
                  activeTab.searchData?.results.map((item) => (
                    <button
                      key={item.url}
                      type="button"
                      onClick={() => loadTargetIntoTab(activeTab.id, item.url, true)}
                      className={`w-full text-left rounded-2xl border p-4 sm:p-5 space-y-1.5 hover:border-sky-400/50 transition-colors ${cardClass}`}
                    >
                      <div className="flex items-center justify-between gap-2 text-xs text-sky-400 font-mono">
                        <span className="truncate">{item.domain}</span>
                        <ExternalLink className="w-3.5 h-3.5 shrink-0" />
                      </div>
                      <div className="text-base font-semibold">{item.title}</div>
                      {item.snippet && (
                        <p className="text-xs text-slate-400 leading-relaxed">{item.snippet}</p>
                      )}
                    </button>
                  ))
                )}
              </div>
            )}
          </div>
        )}

        {/* LIVE WEBPAGE RENDERER */}
        {activeTab.url.startsWith('http') && (
          <WebPageView
            pageData={activeTab.pageData}
            isLoading={activeTab.isLoading}
            error={activeTab.error}
            url={activeTab.url}
            theme={settings.theme}
            readerMode={settings.readerMode}
            desktopMode={settings.desktopMode}
            findQuery={findQuery}
            onNavigate={(nextUrl) => loadTargetIntoTab(activeTab.id, nextUrl, true)}
            onSearchFallback={(q) =>
              loadTargetIntoTab(activeTab.id, `sab://search?q=${encodeURIComponent(q)}`, true)
            }
            onRetry={() => loadTargetIntoTab(activeTab.id, activeTab.url, false)}
            onAskAi={() => handleSendAiPrompt('Summarize this webpage and list key points.')}
          />
        )}
      </main>

      {/* PERSISTENT OPERA-MOBILE 5-SLOT BOTTOM BAR */}
      <nav
        aria-label="Simple AI Browser Bottom Navigation"
        className={`fixed bottom-0 left-0 right-0 z-30 border-t backdrop-blur-md ${chromeClass}`}
      >
        <div className="mx-auto max-w-2xl px-4 h-16 flex items-center justify-between">
          {/* 1. Back */}
          <button
            type="button"
            onClick={handleBack}
            disabled={!canGoBack}
            aria-label="Go Back"
            className="min-w-[48px] min-h-[48px] rounded-2xl flex items-center justify-center hover:bg-white/10 disabled:opacity-30 transition-colors"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>

          {/* 2. Home / Speed Dial */}
          <button
            type="button"
            onClick={() => {
              if (activeTab.url === 'sab://start') {
                setOmniboxOpen(true);
              } else {
                loadTargetIntoTab(activeTab.id, 'sab://start', true);
              }
            }}
            aria-label="Home or Search"
            className="min-w-[48px] min-h-[48px] rounded-2xl flex items-center justify-center hover:bg-white/10 transition-colors"
          >
            <Home className="w-5 h-5" />
          </button>

          {/* 3. Central SAB AI Orb */}
          <button
            type="button"
            onClick={() => setAiOpen(true)}
            aria-label="Open SAB AI"
            className="w-12 h-12 rounded-full bg-[#FF2E4C] text-white flex items-center justify-center shadow-lg shadow-[#FF2E4C]/30 hover:bg-[#e62440] active:scale-95 transition-all"
          >
            <Sparkles className="w-5 h-5" />
          </button>

          {/* 4. Tab Gallery Counter */}
          <button
            type="button"
            onClick={() => setTabsOpen(true)}
            aria-label="Open Tab Gallery"
            className="min-w-[48px] min-h-[48px] rounded-2xl flex items-center justify-center hover:bg-white/10 transition-colors"
          >
            <span className="w-6 h-6 rounded-md border-2 border-current flex items-center justify-center text-[11px] font-bold tabular-nums">
              {visibleTabs.length}
            </span>
          </button>

          {/* 5. Opera Menu Sheet */}
          <button
            type="button"
            onClick={() => setMenuOpen(true)}
            aria-label="Open Menu"
            className="min-w-[48px] min-h-[48px] rounded-2xl flex items-center justify-center hover:bg-white/10 transition-colors"
          >
            <Menu className="w-5 h-5" />
          </button>
        </div>
      </nav>

      {/* ADD SPEED DIAL MODAL */}
      <AnimatePresence>
        {addDialOpen && (
          <div
            className="fixed inset-0 z-40 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4"
            onClick={() => setAddDialOpen(false)}
          >
            <form
              onSubmit={handleAddSpeedDial}
              onClick={(e) => e.stopPropagation()}
              className={`w-full max-w-sm rounded-3xl border p-6 space-y-4 shadow-2xl ${cardClass}`}
            >
              <div className="flex items-center justify-between">
                <h3 className="text-base font-semibold">Add Speed Dial Shortcut</h3>
                <button
                  type="button"
                  onClick={() => setAddDialOpen(false)}
                  className="p-1.5 rounded-lg hover:bg-white/10"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
              <input
                type="text"
                value={newDialName}
                onChange={(e) => setNewDialName(e.target.value)}
                placeholder="Site name (e.g. Reddit)"
                required
                className="w-full px-3.5 py-2.5 rounded-xl border border-white/10 bg-[#0B0D12] text-sm text-white focus:outline-none"
              />
              <input
                type="text"
                value={newDialUrl}
                onChange={(e) => setNewDialUrl(e.target.value)}
                placeholder="URL (e.g. reddit.com)"
                required
                className="w-full px-3.5 py-2.5 rounded-xl border border-white/10 bg-[#0B0D12] text-sm text-white focus:outline-none"
              />
              <button
                type="submit"
                className="w-full py-2.5 rounded-xl bg-[#FF2E4C] text-white text-xs font-semibold"
              >
                Save to Speed Dial
              </button>
            </form>
          </div>
        )}
      </AnimatePresence>

      {/* OVERLAYS */}
      <OmniboxOverlay
        open={omniboxOpen}
        initialValue={activeTab.url === 'sab://start' ? '' : activeTab.url}
        theme={settings.theme}
        history={history}
        bookmarks={bookmarks}
        onClose={() => setOmniboxOpen(false)}
        onNavigateOrSearch={handleNavigateOrSearch}
        onAskAi={handleSendAiPrompt}
      />

      <TabGalleryOverlay
        open={tabsOpen}
        tabs={tabs}
        activeTabId={activeTab.id}
        isPrivateMode={isPrivateMode}
        theme={settings.theme}
        onClose={() => setTabsOpen(false)}
        onSetPrivateMode={(val) => {
          setIsPrivateMode(val);
          const match = tabs.find((t) => t.isPrivate === val);
          if (match) setActiveTabId(match.id);
        }}
        onSelectTab={(id) => setActiveTabId(id)}
        onCreateTab={handleCreateTab}
        onCloseTab={handleCloseTab}
        onClearAllTabs={handleClearAllTabs}
      />

      <MenuSheetOverlay
        open={menuOpen}
        settings={settings}
        hasActivePage={Boolean(activeTab.pageData)}
        isBookmarked={isCurrentBookmarked}
        onClose={() => setMenuOpen(false)}
        onUpdateSettings={handleUpdateSettings}
        onToggleBookmark={handleToggleBookmark}
        onOpenLibrary={() => setLibraryOpen(true)}
        onToggleFindInPage={() => setFindBarOpen((v) => !v)}
        onDownloadPageText={handleDownloadPageText}
        onReplaySplash={() => setSplashRunId((n) => n + 1)}
      />

      <LibraryOverlay
        open={libraryOpen}
        theme={settings.theme}
        bookmarks={bookmarks}
        history={history}
        onClose={() => setLibraryOpen(false)}
        onOpenUrl={(url) => loadTargetIntoTab(activeTab.id, url, true)}
        onRemoveBookmark={(id) =>
          setBookmarks((prev) => prev.filter((b) => b.id !== id))
        }
        onClearHistory={() => setHistory([])}
      />

      <SabAiSheet
        open={aiOpen}
        onClose={() => setAiOpen(false)}
        theme={settings.theme}
        activePage={activeTab.pageData}
        messages={aiMessages}
        isThinking={aiThinking}
        onSendPrompt={handleSendAiPrompt}
        onClearChat={() => setAiMessages([])}
        onOpenSourceUrl={(url) => loadTargetIntoTab(activeTab.id, url, true)}
      />

      {/* SAB SPLASH SCREEN ANIMATION */}
      <AnimatePresence>
        {showSplash && <SplashOverlay onDismiss={() => setShowSplash(false)} />}
      </AnimatePresence>
    </div>
  );
}
