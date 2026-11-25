import {useState} from 'react';
import {AnimatePresence, motion} from 'motion/react';
import {
  Bookmark,
  BookOpen,
  Check,
  Clock,
  Download,
  EyeOff,
  Globe,
  History,
  Monitor,
  Plus,
  RotateCcw,
  Search,
  Shield,
  Sparkles,
  Trash2,
  X,
} from 'lucide-react';
import type {
  BookmarkItem,
  BrowserSettings,
  BrowserTab,
  BrowserTheme,
  HistoryEntry,
  TileShape,
} from '../types/browser';

/* ================= OMNIBOX OVERLAY ================= */
interface OmniboxOverlayProps {
  open: boolean;
  initialValue: string;
  theme: BrowserTheme;
  history: HistoryEntry[];
  bookmarks: BookmarkItem[];
  onClose: () => void;
  onNavigateOrSearch: (value: string) => void;
  onAskAi: (prompt: string) => void;
}

export function OmniboxOverlay({
  open,
  initialValue,
  theme,
  history,
  bookmarks,
  onClose,
  onNavigateOrSearch,
  onAskAi,
}: OmniboxOverlayProps) {
  const [query, setQuery] = useState(initialValue);
  const isLight = theme === 'daylight';

  const trimmed = query.trim().toLowerCase();
  const suggestions = [...bookmarks, ...history]
    .filter(
      (item, index, self) =>
        self.findIndex((x) => x.url === item.url) === index &&
        (!trimmed ||
          item.title.toLowerCase().includes(trimmed) ||
          item.url.toLowerCase().includes(trimmed)),
    )
    .slice(0, 6);

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{opacity: 0}}
          animate={{opacity: 1}}
          exit={{opacity: 0}}
          className="fixed inset-0 z-40 bg-black/65 backdrop-blur-xs flex flex-col justify-end sm:justify-center sm:items-center sm:p-4"
          onClick={onClose}
        >
          <motion.div
            initial={{y: 28, opacity: 0}}
            animate={{y: 0, opacity: 1}}
            exit={{y: 28, opacity: 0}}
            transition={{duration: 0.18}}
            onClick={(e) => e.stopPropagation()}
            className={`w-full sm:max-w-2xl rounded-t-3xl sm:rounded-3xl border p-5 space-y-4 shadow-2xl ${
              isLight
                ? 'bg-white border-slate-200 text-slate-900'
                : 'bg-[#141821] border-white/10 text-slate-100'
            }`}
          >
            <div className="flex items-center justify-between">
              <span className="text-sm font-semibold">Search Web, Enter URL, or Ask AI</span>
              <button
                type="button"
                onClick={onClose}
                className="p-2 rounded-xl hover:bg-white/10"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (!query.trim()) return;
                onNavigateOrSearch(query.trim());
                onClose();
              }}
              className="space-y-3"
            >
              <div
                className={`flex items-center gap-2.5 p-2.5 rounded-2xl border ${
                  isLight ? 'bg-slate-50 border-slate-200' : 'bg-[#0B0D12] border-white/10'
                }`}
              >
                <Globe className="w-4 h-4 text-sky-400 ml-1 shrink-0" />
                <input
                  type="text"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="https://example.com or search anything..."
                  autoFocus
                  className="flex-1 bg-transparent text-sm focus:outline-none"
                />
                {query && (
                  <button
                    type="button"
                    onClick={() => setQuery('')}
                    className="p-1.5 rounded-lg hover:bg-white/10"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                )}
              </div>

              <div className="grid grid-cols-2 gap-2.5">
                <button
                  type="submit"
                  className="py-2.5 px-4 rounded-xl bg-[#FF2E4C] text-white text-xs font-semibold inline-flex items-center justify-center gap-2 hover:bg-[#e62440] transition-colors"
                >
                  <Search className="w-3.5 h-3.5" />
                  Search / Open URL
                </button>
                <button
                  type="button"
                  onClick={() => {
                    if (!query.trim()) return;
                    onAskAi(query.trim());
                    onClose();
                  }}
                  className={`py-2.5 px-4 rounded-xl border text-xs font-semibold inline-flex items-center justify-center gap-2 ${
                    isLight
                      ? 'bg-slate-50 border-slate-200 text-slate-900'
                      : 'bg-[#0B0D12] border-white/10 text-white'
                  }`}
                >
                  <Sparkles className="w-3.5 h-3.5 text-[#FF2E4C]" />
                  Ask SAB AI
                </button>
              </div>
            </form>

            {suggestions.length > 0 && (
              <div className="space-y-1.5 max-h-60 overflow-y-auto pt-1">
                <div className="text-[11px] font-medium text-slate-400">
                  Bookmarks &amp; Recent History
                </div>
                {suggestions.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => {
                      onNavigateOrSearch(item.url);
                      onClose();
                    }}
                    className={`w-full flex items-center justify-between gap-3 px-3.5 py-2.5 rounded-xl border text-left hover:border-sky-400/40 transition-colors ${
                      isLight ? 'bg-slate-50 border-slate-200' : 'bg-[#0B0D12] border-white/10'
                    }`}
                  >
                    <div className="min-w-0">
                      <div className="text-xs font-medium truncate">{item.title}</div>
                      <div className="text-[11px] text-slate-400 truncate">{item.url}</div>
                    </div>
                    <Clock className="w-3.5 h-3.5 text-slate-500 shrink-0" />
                  </button>
                ))}
              </div>
            )}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

/* ================= TAB GALLERY & TAB ISLANDS ================= */
interface TabGalleryOverlayProps {
  open: boolean;
  tabs: BrowserTab[];
  activeTabId: string;
  isPrivateMode: boolean;
  theme: BrowserTheme;
  onClose: () => void;
  onSetPrivateMode: (val: boolean) => void;
  onSelectTab: (id: string) => void;
  onCreateTab: (isPrivate: boolean) => void;
  onCloseTab: (id: string) => void;
  onClearAllTabs: (isPrivate: boolean) => void;
}

export function TabGalleryOverlay({
  open,
  tabs,
  activeTabId,
  isPrivateMode,
  theme,
  onClose,
  onSetPrivateMode,
  onSelectTab,
  onCreateTab,
  onCloseTab,
  onClearAllTabs,
}: TabGalleryOverlayProps) {
  const isLight = theme === 'daylight';
  const filteredTabs = tabs.filter((t) => t.isPrivate === isPrivateMode);

  // Group tabs by Tab Island (Opera Tab Islands pattern)
  const islands = Array.from(new Set(filteredTabs.map((t) => t.island)));

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{opacity: 0}}
          animate={{opacity: 1}}
          exit={{opacity: 0}}
          className="fixed inset-0 z-40 bg-black/65 backdrop-blur-xs flex items-end sm:items-center justify-center sm:p-4"
          onClick={onClose}
        >
          <motion.div
            initial={{y: 36, opacity: 0}}
            animate={{y: 0, opacity: 1}}
            exit={{y: 36, opacity: 0}}
            transition={{duration: 0.2}}
            onClick={(e) => e.stopPropagation()}
            className={`w-full sm:max-w-3xl h-[82vh] rounded-t-3xl sm:rounded-3xl border flex flex-col overflow-hidden shadow-2xl ${
              isLight
                ? 'bg-white border-slate-200 text-slate-900'
                : 'bg-[#141821] border-white/10 text-slate-100'
            }`}
          >
            <div className="px-5 py-4 border-b border-white/10 flex items-center justify-between gap-3">
              <div className="flex items-center gap-1.5 p-1 rounded-xl bg-black/20 border border-white/10">
                <button
                  type="button"
                  onClick={() => onSetPrivateMode(false)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors ${
                    !isPrivateMode ? 'bg-[#FF2E4C] text-white' : 'text-slate-400'
                  }`}
                >
                  Normal ({tabs.filter((t) => !t.isPrivate).length})
                </button>
                <button
                  type="button"
                  onClick={() => onSetPrivateMode(true)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-semibold inline-flex items-center gap-1.5 transition-colors ${
                    isPrivateMode ? 'bg-violet-600 text-white' : 'text-slate-400'
                  }`}
                >
                  <EyeOff className="w-3.5 h-3.5" />
                  Private ({tabs.filter((t) => t.isPrivate).length})
                </button>
              </div>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => onClearAllTabs(isPrivateMode)}
                  className="text-xs text-slate-400 hover:text-white px-2.5 py-1.5 rounded-lg"
                >
                  Close All
                </button>
                <button
                  type="button"
                  onClick={onClose}
                  className="p-2 rounded-xl hover:bg-white/10"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            </div>

            <div className="flex-1 overflow-y-auto p-5 space-y-6">
              {filteredTabs.length === 0 ? (
                <div className="text-center py-12 space-y-3">
                  <p className="text-sm text-slate-400">
                    No {isPrivateMode ? 'private ' : ''}tabs open.
                  </p>
                  <button
                    type="button"
                    onClick={() => onCreateTab(isPrivateMode)}
                    className="px-4 py-2 rounded-xl bg-[#FF2E4C] text-white text-xs font-semibold"
                  >
                    Open New Tab
                  </button>
                </div>
              ) : (
                islands.map((islandName) => (
                  <div key={islandName} className="space-y-2.5">
                    <div className="text-xs font-semibold text-sky-400">
                      {islandName} Island
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      {filteredTabs
                        .filter((t) => t.island === islandName)
                        .map((tab) => {
                          const isCurrent = tab.id === activeTabId;
                          return (
                            <div
                              key={tab.id}
                              className={`rounded-2xl border p-4 flex items-center justify-between gap-3 transition-colors ${
                                isCurrent
                                  ? 'border-[#FF2E4C] bg-[#FF2E4C]/10'
                                  : isLight
                                    ? 'bg-slate-50 border-slate-200'
                                    : 'bg-[#0B0D12] border-white/10'
                              }`}
                            >
                              <button
                                type="button"
                                onClick={() => {
                                  onSelectTab(tab.id);
                                  onClose();
                                }}
                                className="flex-1 text-left min-w-0"
                              >
                                <div className="text-sm font-semibold truncate">{tab.title}</div>
                                <div className="text-xs text-slate-400 truncate mt-0.5">
                                  {tab.url}
                                </div>
                              </button>
                              <button
                                type="button"
                                onClick={() => onCloseTab(tab.id)}
                                aria-label="Close tab"
                                className="p-2 rounded-xl hover:bg-white/10 shrink-0"
                              >
                                <X className="w-4 h-4" />
                              </button>
                            </div>
                          );
                        })}
                    </div>
                  </div>
                ))
              )}
            </div>

            <div className="p-4 border-t border-white/10">
              <button
                type="button"
                onClick={() => {
                  onCreateTab(isPrivateMode);
                  onClose();
                }}
                className="w-full py-3 rounded-2xl bg-[#FF2E4C] text-white text-xs font-semibold inline-flex items-center justify-center gap-2 hover:bg-[#e62440] transition-colors"
              >
                <Plus className="w-4 h-4" />
                New {isPrivateMode ? 'Private ' : ''}Tab
              </button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

/* ================= OPERA BOTTOM MENU SHEET ================= */
interface MenuSheetOverlayProps {
  open: boolean;
  settings: BrowserSettings;
  hasActivePage: boolean;
  isBookmarked: boolean;
  onClose: () => void;
  onUpdateSettings: (patch: Partial<BrowserSettings>) => void;
  onToggleBookmark: () => void;
  onOpenLibrary: () => void;
  onToggleFindInPage: () => void;
  onDownloadPageText: () => void;
  onReplaySplash: () => void;
}

export function MenuSheetOverlay({
  open,
  settings,
  hasActivePage,
  isBookmarked,
  onClose,
  onUpdateSettings,
  onToggleBookmark,
  onOpenLibrary,
  onToggleFindInPage,
  onDownloadPageText,
  onReplaySplash,
}: MenuSheetOverlayProps) {
  const isLight = settings.theme === 'daylight';
  const cardClass = isLight
    ? 'bg-slate-50 border-slate-200 text-slate-900'
    : 'bg-[#0B0D12] border-white/10 text-slate-100';

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{opacity: 0}}
          animate={{opacity: 1}}
          exit={{opacity: 0}}
          className="fixed inset-0 z-40 bg-black/60 backdrop-blur-xs flex items-end justify-center sm:p-4"
          onClick={onClose}
        >
          <motion.div
            initial={{y: '100%'}}
            animate={{y: 0}}
            exit={{y: '100%'}}
            transition={{duration: 0.22, ease: [0.16, 1, 0.3, 1]}}
            onClick={(e) => e.stopPropagation()}
            className={`w-full sm:max-w-xl rounded-t-3xl sm:rounded-3xl border p-5 space-y-5 shadow-2xl ${
              isLight
                ? 'bg-white border-slate-200 text-slate-900'
                : 'bg-[#141821] border-white/10 text-slate-100'
            }`}
          >
            <div className="w-10 h-1 rounded-full bg-slate-500/40 mx-auto" />

            {/* Quick Toggles */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
              <button
                type="button"
                onClick={() =>
                  onUpdateSettings({adBlockEnabled: !settings.adBlockEnabled})
                }
                className={`p-3 rounded-2xl border text-left space-y-1.5 ${
                  settings.adBlockEnabled
                    ? 'border-emerald-500/40 bg-emerald-500/10'
                    : cardClass
                }`}
              >
                <div className="flex items-center justify-between">
                  <Shield className="w-4 h-4 text-emerald-400" />
                  {settings.adBlockEnabled && <Check className="w-3.5 h-3.5 text-emerald-400" />}
                </div>
                <div className="text-xs font-semibold">Ad Blocker</div>
              </button>

              <button
                type="button"
                onClick={() => onUpdateSettings({readerMode: !settings.readerMode})}
                className={`p-3 rounded-2xl border text-left space-y-1.5 ${
                  settings.readerMode
                    ? 'border-[#FF2E4C]/50 bg-[#FF2E4C]/10'
                    : cardClass
                }`}
              >
                <BookOpen className="w-4 h-4 text-[#FF2E4C]" />
                <div className="text-xs font-semibold">Reader Mode</div>
              </button>

              <button
                type="button"
                onClick={() => onUpdateSettings({desktopMode: !settings.desktopMode})}
                className={`p-3 rounded-2xl border text-left space-y-1.5 ${
                  settings.desktopMode
                    ? 'border-sky-400/50 bg-sky-500/10'
                    : cardClass
                }`}
              >
                <Monitor className="w-4 h-4 text-sky-400" />
                <div className="text-xs font-semibold">Desktop UA</div>
              </button>

              <button
                type="button"
                onClick={() => {
                  onToggleBookmark();
                  onClose();
                }}
                disabled={!hasActivePage}
                className={`p-3 rounded-2xl border text-left space-y-1.5 disabled:opacity-40 ${
                  isBookmarked ? 'border-amber-400/50 bg-amber-500/10' : cardClass
                }`}
              >
                <Bookmark className="w-4 h-4 text-amber-400" />
                <div className="text-xs font-semibold">
                  {isBookmarked ? 'Bookmarked' : 'Bookmark'}
                </div>
              </button>
            </div>

            {/* Theme & Speed Dial Tile Shapes */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <div className="text-xs font-medium text-slate-400">Theme</div>
                <div className="grid grid-cols-3 gap-1.5">
                  {(['obsidian', 'midnight', 'daylight'] as BrowserTheme[]).map((t) => (
                    <button
                      key={t}
                      type="button"
                      onClick={() => onUpdateSettings({theme: t})}
                      className={`py-2 rounded-xl text-xs font-medium capitalize border ${
                        settings.theme === t
                          ? 'bg-[#FF2E4C] text-white border-[#FF2E4C]'
                          : cardClass
                      }`}
                    >
                      {t}
                    </button>
                  ))}
                </div>
              </div>

              <div className="space-y-2">
                <div className="text-xs font-medium text-slate-400">Speed Dial Shape</div>
                <div className="grid grid-cols-3 gap-1.5">
                  {(['squircle', 'circle', 'square'] as TileShape[]).map((s) => (
                    <button
                      key={s}
                      type="button"
                      onClick={() => onUpdateSettings({tileShape: s})}
                      className={`py-2 rounded-xl text-xs font-medium capitalize border ${
                        settings.tileShape === s
                          ? 'bg-[#FF2E4C] text-white border-[#FF2E4C]'
                          : cardClass
                      }`}
                    >
                      {s}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {/* Action Rows */}
            <div className="grid grid-cols-2 gap-2.5 pt-1">
              <button
                type="button"
                onClick={() => {
                  onOpenLibrary();
                  onClose();
                }}
                className={`flex items-center gap-2.5 px-3.5 py-2.5 rounded-xl border text-xs font-medium ${cardClass}`}
              >
                <History className="w-4 h-4 text-sky-400" />
                Bookmarks &amp; History
              </button>

              <button
                type="button"
                disabled={!hasActivePage}
                onClick={() => {
                  onToggleFindInPage();
                  onClose();
                }}
                className={`flex items-center gap-2.5 px-3.5 py-2.5 rounded-xl border text-xs font-medium disabled:opacity-40 ${cardClass}`}
              >
                <Search className="w-4 h-4 text-amber-400" />
                Find in Page
              </button>

              <button
                type="button"
                disabled={!hasActivePage}
                onClick={() => {
                  onDownloadPageText();
                  onClose();
                }}
                className={`flex items-center gap-2.5 px-3.5 py-2.5 rounded-xl border text-xs font-medium disabled:opacity-40 ${cardClass}`}
              >
                <Download className="w-4 h-4 text-emerald-400" />
                Save Page Text
              </button>

              <button
                type="button"
                onClick={() => {
                  onReplaySplash();
                  onClose();
                }}
                className={`flex items-center gap-2.5 px-3.5 py-2.5 rounded-xl border text-xs font-medium ${cardClass}`}
              >
                <RotateCcw className="w-4 h-4 text-[#FF2E4C]" />
                Replay Splash
              </button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

/* ================= BOOKMARKS & HISTORY LIBRARY ================= */
interface LibraryOverlayProps {
  open: boolean;
  theme: BrowserTheme;
  bookmarks: BookmarkItem[];
  history: HistoryEntry[];
  onClose: () => void;
  onOpenUrl: (url: string) => void;
  onRemoveBookmark: (id: string) => void;
  onClearHistory: () => void;
}

export function LibraryOverlay({
  open,
  theme,
  bookmarks,
  history,
  onClose,
  onOpenUrl,
  onRemoveBookmark,
  onClearHistory,
}: LibraryOverlayProps) {
  const [tab, setTab] = useState<'bookmarks' | 'history'>('bookmarks');
  const isLight = theme === 'daylight';

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{opacity: 0}}
          animate={{opacity: 1}}
          exit={{opacity: 0}}
          className="fixed inset-0 z-40 bg-black/65 backdrop-blur-xs flex items-end sm:items-center justify-center sm:p-4"
          onClick={onClose}
        >
          <motion.div
            initial={{y: 36, opacity: 0}}
            animate={{y: 0, opacity: 1}}
            exit={{y: 36, opacity: 0}}
            onClick={(e) => e.stopPropagation()}
            className={`w-full sm:max-w-2xl h-[80vh] rounded-t-3xl sm:rounded-3xl border flex flex-col overflow-hidden shadow-2xl ${
              isLight
                ? 'bg-white border-slate-200 text-slate-900'
                : 'bg-[#141821] border-white/10 text-slate-100'
            }`}
          >
            <div className="px-5 py-4 border-b border-white/10 flex items-center justify-between">
              <div className="flex items-center gap-1.5 p-1 rounded-xl bg-black/20 border border-white/10">
                <button
                  type="button"
                  onClick={() => setTab('bookmarks')}
                  className={`px-3 py-1.5 rounded-lg text-xs font-semibold ${
                    tab === 'bookmarks' ? 'bg-[#FF2E4C] text-white' : 'text-slate-400'
                  }`}
                >
                  Bookmarks ({bookmarks.length})
                </button>
                <button
                  type="button"
                  onClick={() => setTab('history')}
                  className={`px-3 py-1.5 rounded-lg text-xs font-semibold ${
                    tab === 'history' ? 'bg-[#FF2E4C] text-white' : 'text-slate-400'
                  }`}
                >
                  History ({history.length})
                </button>
              </div>
              <div className="flex items-center gap-2">
                {tab === 'history' && history.length > 0 && (
                  <button
                    type="button"
                    onClick={onClearHistory}
                    className="text-xs text-slate-400 hover:text-white px-2.5 py-1.5"
                  >
                    Clear History
                  </button>
                )}
                <button type="button" onClick={onClose} className="p-2 rounded-xl hover:bg-white/10">
                  <X className="w-4 h-4" />
                </button>
              </div>
            </div>

            <div className="flex-1 overflow-y-auto p-5 space-y-2.5">
              {tab === 'bookmarks' ? (
                bookmarks.length === 0 ? (
                  <p className="text-sm text-slate-400 text-center py-12">
                    No bookmarks saved yet.
                  </p>
                ) : (
                  bookmarks.map((bm) => (
                    <div
                      key={bm.id}
                      className={`rounded-2xl border p-3.5 flex items-center justify-between gap-3 ${
                        isLight ? 'bg-slate-50 border-slate-200' : 'bg-[#0B0D12] border-white/10'
                      }`}
                    >
                      <button
                        type="button"
                        onClick={() => {
                          onOpenUrl(bm.url);
                          onClose();
                        }}
                        className="flex-1 text-left min-w-0"
                      >
                        <div className="text-xs font-semibold truncate">{bm.title}</div>
                        <div className="text-[11px] text-slate-400 truncate">{bm.url}</div>
                      </button>
                      <button
                        type="button"
                        onClick={() => onRemoveBookmark(bm.id)}
                        className="p-2 rounded-xl hover:bg-white/10 text-slate-400"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  ))
                )
              ) : history.length === 0 ? (
                <p className="text-sm text-slate-400 text-center py-12">
                  No browsing history recorded yet.
                </p>
              ) : (
                history.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => {
                      onOpenUrl(item.url);
                      onClose();
                    }}
                    className={`w-full rounded-2xl border p-3.5 text-left flex items-center justify-between gap-3 ${
                      isLight ? 'bg-slate-50 border-slate-200' : 'bg-[#0B0D12] border-white/10'
                    }`}
                  >
                    <div className="min-w-0">
                      <div className="text-xs font-semibold truncate">{item.title}</div>
                      <div className="text-[11px] text-slate-400 truncate">{item.url}</div>
                    </div>
                    <span className="text-[11px] text-slate-500 tabular-nums shrink-0">
                      {new Date(item.visitedAt).toLocaleTimeString([], {
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </span>
                  </button>
                ))
              )}
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
