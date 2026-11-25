import {AlertTriangle, ExternalLink, Globe, RefreshCw, Search, Shield} from 'lucide-react';
import type {BrowserTheme, ParsedInlineSegment, ParsedPageData} from '../types/browser';

interface WebPageViewProps {
  pageData: ParsedPageData | null;
  isLoading: boolean;
  error: string | null;
  url: string;
  theme: BrowserTheme;
  readerMode: boolean;
  desktopMode: boolean;
  findQuery: string;
  onNavigate: (url: string) => void;
  onSearchFallback: (query: string) => void;
  onRetry: () => void;
  onAskAi: () => void;
}

function HighlightedText({text, query}: {text: string; query: string}) {
  if (!query.trim()) return <>{text}</>;
  const escaped = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const regex = new RegExp(`(${escaped})`, 'gi');
  const parts = text.split(regex);
  return (
    <>
      {parts.map((part, i) =>
        regex.test(part) ? (
          <mark key={i} className="bg-amber-400/90 text-slate-950 rounded px-0.5">
            {part}
          </mark>
        ) : (
          <span key={i}>{part}</span>
        ),
      )}
    </>
  );
}

function InlineSegments({
  segments,
  query,
  onNavigate,
}: {
  segments: ParsedInlineSegment[];
  query: string;
  onNavigate: (url: string) => void;
}) {
  return (
    <>
      {segments.map((seg, idx) =>
        seg.href ? (
          <button
            key={idx}
            type="button"
            onClick={() => onNavigate(seg.href!)}
            className="text-sky-400 hover:text-sky-300 underline underline-offset-2 text-left inline"
          >
            <HighlightedText text={seg.text} query={query} />
          </button>
        ) : (
          <span key={idx}>
            <HighlightedText text={seg.text} query={query} />
          </span>
        ),
      )}
    </>
  );
}

export function WebPageView({
  pageData,
  isLoading,
  error,
  url,
  theme,
  readerMode,
  desktopMode,
  findQuery,
  onNavigate,
  onSearchFallback,
  onRetry,
  onAskAi,
}: WebPageViewProps) {
  const isLight = theme === 'daylight';
  const surfaceClass = isLight
    ? 'bg-white border-slate-200 text-slate-900'
    : 'bg-[#141821] border-white/10 text-slate-100';
  const mutedClass = isLight ? 'text-slate-600' : 'text-slate-400';

  if (isLoading && !pageData) {
    return (
      <div className="mx-auto max-w-4xl px-4 sm:px-6 py-10 space-y-6">
        <div className={`rounded-2xl border p-6 space-y-4 animate-pulse ${surfaceClass}`}>
          <div className="h-4 w-40 rounded bg-slate-700/40" />
          <div className="h-8 w-3/4 rounded bg-slate-700/40" />
          <div className="space-y-2.5 pt-2">
            <div className="h-4 w-full rounded bg-slate-700/30" />
            <div className="h-4 w-5/6 rounded bg-slate-700/30" />
            <div className="h-4 w-2/3 rounded bg-slate-700/30" />
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="mx-auto max-w-2xl px-4 sm:px-6 py-14">
        <div className={`rounded-2xl border p-6 sm:p-8 space-y-4 ${surfaceClass}`}>
          <div className="flex items-center gap-3 text-[#FF2E4C]">
            <AlertTriangle className="w-6 h-6 shrink-0" />
            <h2 className="text-lg font-semibold">Unable to Load Page</h2>
          </div>
          <p className={`text-sm leading-relaxed ${mutedClass}`}>{error}</p>
          <div className="text-xs font-mono break-all opacity-75">{url}</div>
          <div className="flex flex-wrap items-center gap-3 pt-2">
            <button
              type="button"
              onClick={onRetry}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-[#FF2E4C] text-white text-xs font-semibold hover:bg-[#e62440] transition-colors"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              Retry Page
            </button>
            <button
              type="button"
              onClick={() => onSearchFallback(url.replace(/^https?:\/\//, ''))}
              className={`inline-flex items-center gap-2 px-4 py-2 rounded-xl border text-xs font-medium ${surfaceClass}`}
            >
              <Search className="w-3.5 h-3.5" />
              Search Web Instead
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (!pageData) return null;

  const containerMaxWidth = readerMode
    ? 'max-w-2xl'
    : desktopMode
      ? 'max-w-6xl'
      : 'max-w-4xl';

  return (
    <div className={`mx-auto ${containerMaxWidth} px-4 sm:px-6 py-6 space-y-6`}>
      {/* Site Header Banner */}
      <div className={`rounded-2xl border p-5 sm:p-6 space-y-4 ${surfaceClass}`}>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2.5 min-w-0">
            {pageData.favicon ? (
              <img
                src={pageData.favicon}
                alt=""
                referrerPolicy="no-referrer"
                onError={(e) => {
                  (e.currentTarget as HTMLImageElement).style.display = 'none';
                }}
                className="w-5 h-5 rounded object-contain shrink-0"
              />
            ) : (
              <Globe className="w-4 h-4 text-sky-400 shrink-0" />
            )}
            <span className={`text-xs font-mono truncate ${mutedClass}`}>
              {pageData.domain}
            </span>
          </div>

          <div className="flex items-center gap-3">
            {pageData.blockedCount > 0 && (
              <span className="inline-flex items-center gap-1.5 text-xs text-emerald-400 font-medium tabular-nums">
                <Shield className="w-3.5 h-3.5" />
                {pageData.blockedCount} blocked
              </span>
            )}
            <button
              type="button"
              onClick={onAskAi}
              className="px-3 py-1.5 rounded-lg bg-[#FF2E4C] text-white text-xs font-semibold hover:bg-[#e62440] transition-colors whitespace-nowrap shrink-0"
            >
              Summarize with AI
            </button>
          </div>
        </div>

        <h1 className="text-2xl sm:text-3xl font-bold tracking-tight leading-tight">
          <HighlightedText text={pageData.title} query={findQuery} />
        </h1>

        {pageData.description && (
          <p className={`text-sm leading-relaxed ${mutedClass}`}>
            <HighlightedText text={pageData.description} query={findQuery} />
          </p>
        )}

        {/* Site Navigation Links */}
        {!readerMode && pageData.navLinks.length > 0 && (
          <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-white/10">
            {pageData.navLinks.map((link) => (
              <button
                key={link.url}
                type="button"
                onClick={() => onNavigate(link.url)}
                className={`px-2.5 py-1.5 rounded-lg text-xs font-medium border transition-colors hover:border-[#FF2E4C]/50 ${
                  isLight
                    ? 'bg-slate-50 border-slate-200 text-slate-700'
                    : 'bg-[#0B0D12] border-white/10 text-slate-300'
                }`}
              >
                {link.label}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Rendered Page Content */}
      <article className={`rounded-2xl border p-5 sm:p-8 space-y-5 ${surfaceClass}`}>
        {pageData.blocks.length === 0 ? (
          <div className="space-y-4">
            <p className={`text-sm ${mutedClass}`}>
              This page relies primarily on client-side rendering or compact links. Available links
              extracted from the page:
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
              {pageData.pageLinks.map((link) => (
                <button
                  key={link.url}
                  type="button"
                  onClick={() => onNavigate(link.url)}
                  className={`flex items-center justify-between gap-2 px-3.5 py-2.5 rounded-xl border text-left text-xs font-medium hover:border-sky-400/50 transition-colors ${
                    isLight ? 'bg-slate-50 border-slate-200' : 'bg-[#0B0D12] border-white/10'
                  }`}
                >
                  <span className="truncate">{link.label}</span>
                  <ExternalLink className="w-3.5 h-3.5 shrink-0 opacity-60" />
                </button>
              ))}
            </div>
          </div>
        ) : (
          pageData.blocks.map((block, index) => {
            if (block.type === 'heading' && block.text) {
              const sizeClass =
                block.level === 1
                  ? 'text-2xl font-bold pt-2'
                  : block.level === 2
                    ? 'text-xl font-semibold pt-2'
                    : 'text-base font-semibold pt-1';
              return (
                <h2 key={index} className={sizeClass}>
                  <HighlightedText text={block.text} query={findQuery} />
                </h2>
              );
            }

            if (block.type === 'paragraph' && block.segments) {
              return (
                <p
                  key={index}
                  className={`${
                    readerMode ? 'text-base leading-8' : 'text-[15px] leading-7'
                  } opacity-95`}
                >
                  <InlineSegments
                    segments={block.segments}
                    query={findQuery}
                    onNavigate={onNavigate}
                  />
                </p>
              );
            }

            if (block.type === 'list' && block.items) {
              return (
                <ul key={index} className="list-disc pl-6 space-y-2 text-[15px] leading-relaxed">
                  {block.items.map((itemSegs, itemIdx) => (
                    <li key={itemIdx}>
                      <InlineSegments
                        segments={itemSegs}
                        query={findQuery}
                        onNavigate={onNavigate}
                      />
                    </li>
                  ))}
                </ul>
              );
            }

            if (block.type === 'blockquote' && block.text) {
              return (
                <blockquote
                  key={index}
                  className={`rounded-xl border p-4 italic text-sm leading-relaxed ${
                    isLight ? 'bg-slate-50 border-slate-200' : 'bg-[#0B0D12] border-white/10'
                  }`}
                >
                  <HighlightedText text={block.text} query={findQuery} />
                </blockquote>
              );
            }

            if (block.type === 'code' && block.text) {
              return (
                <pre
                  key={index}
                  className="rounded-xl bg-[#0B0D12] border border-white/10 p-4 overflow-x-auto text-xs font-mono text-slate-200 leading-relaxed"
                >
                  {block.text}
                </pre>
              );
            }

            if (block.type === 'image' && block.src && !readerMode) {
              return (
                <figure key={index} className="space-y-1.5">
                  <img
                    src={block.src}
                    alt={block.alt || 'Webpage image'}
                    referrerPolicy="no-referrer"
                    loading="lazy"
                    onError={(e) => {
                      (e.currentTarget as HTMLImageElement).style.display = 'none';
                    }}
                    className="max-h-96 w-auto rounded-xl border border-white/10 object-contain"
                  />
                  {block.alt && (
                    <figcaption className={`text-xs ${mutedClass}`}>{block.alt}</figcaption>
                  )}
                </figure>
              );
            }

            return null;
          })
        )}
      </article>

      {/* Page Directory Links */}
      {!readerMode && pageData.pageLinks.length > 0 && pageData.blocks.length > 0 && (
        <section className={`rounded-2xl border p-5 space-y-3 ${surfaceClass}`}>
          <h3 className="text-xs font-semibold uppercase tracking-wider opacity-75">
            Links on this page
          </h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
            {pageData.pageLinks.slice(0, 12).map((link) => (
              <button
                key={link.url}
                type="button"
                onClick={() => onNavigate(link.url)}
                className={`flex items-center justify-between gap-2 px-3 py-2 rounded-xl border text-left text-xs hover:border-sky-400/50 transition-colors ${
                  isLight ? 'bg-slate-50 border-slate-200' : 'bg-[#0B0D12] border-white/10'
                }`}
              >
                <span className="truncate">{link.label}</span>
                <ExternalLink className="w-3 h-3 shrink-0 opacity-50" />
              </button>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
