export type TileShape = 'squircle' | 'circle' | 'square';
export type BrowserTheme = 'obsidian' | 'daylight' | 'midnight';

export interface ParsedInlineSegment {
  text: string;
  href?: string;
}

export interface ParsedPageBlock {
  type: 'heading' | 'paragraph' | 'list' | 'blockquote' | 'code' | 'image';
  level?: number;
  text?: string;
  segments?: ParsedInlineSegment[];
  items?: ParsedInlineSegment[][];
  src?: string;
  alt?: string;
}

export interface ParsedPageData {
  finalUrl: string;
  domain: string;
  isSecure: boolean;
  title: string;
  description: string;
  favicon: string | null;
  blockedCount: number;
  navLinks: {label: string; url: string}[];
  blocks: ParsedPageBlock[];
  pageLinks: {label: string; url: string}[];
  textContent: string;
}

export interface SearchResultItem {
  title: string;
  url: string;
  domain: string;
  snippet: string;
}

export interface SpeedDialItem {
  id: string;
  name: string;
  url: string;
  shortLabel: string;
  accent: string;
}

export interface BookmarkItem {
  id: string;
  title: string;
  url: string;
  domain: string;
  addedAt: number;
}

export interface HistoryEntry {
  id: string;
  title: string;
  url: string;
  domain: string;
  visitedAt: number;
}

export interface BrowserTab {
  id: string;
  title: string;
  url: string;
  island: string;
  isPrivate: boolean;
  historyStack: string[];
  historyIndex: number;
  isLoading: boolean;
  error: string | null;
  pageData: ParsedPageData | null;
  searchData: {query: string; results: SearchResultItem[]} | null;
  blockedCount: number;
}

export interface AiChatMessage {
  id: string;
  role: 'user' | 'assistant' | 'error';
  text: string;
  sources?: {uri: string; title: string}[];
}

export interface BrowserSettings {
  theme: BrowserTheme;
  tileShape: TileShape;
  adBlockEnabled: boolean;
  readerMode: boolean;
  desktopMode: boolean;
}

export const DEFAULT_SPEED_DIAL: SpeedDialItem[] = [
  {
    id: 'wiki',
    name: 'Wikipedia',
    url: 'https://en.wikipedia.org/wiki/Main_Page',
    shortLabel: 'W',
    accent: '#38BDF8',
  },
  {
    id: 'hn',
    name: 'Hacker News',
    url: 'https://news.ycombinator.com',
    shortLabel: 'HN',
    accent: '#FB923C',
  },
  {
    id: 'github',
    name: 'GitHub',
    url: 'https://github.com/Raj-Dev-Lab/Simple-Ai-Browser',
    shortLabel: 'GH',
    accent: '#A78BFA',
  },
  {
    id: 'mdn',
    name: 'MDN Docs',
    url: 'https://developer.mozilla.org/en-US/docs/Web',
    shortLabel: 'M',
    accent: '#F59E0B',
  },
  {
    id: 'arxiv',
    name: 'arXiv',
    url: 'https://arxiv.org',
    shortLabel: 'AX',
    accent: '#10B981',
  },
  {
    id: 'bbc',
    name: 'BBC News',
    url: 'https://www.bbc.com/news',
    shortLabel: 'BBC',
    accent: '#FF2E4C',
  },
  {
    id: 'ddg',
    name: 'DuckDuckGo',
    url: 'https://html.duckduckgo.com/html/',
    shortLabel: 'D',
    accent: '#F97316',
  },
  {
    id: 'mozilla',
    name: 'GeckoView',
    url: 'https://firefox-source-docs.mozilla.org/mobile/android/geckoview/',
    shortLabel: 'GV',
    accent: '#60A5FA',
  },
];
