import 'dotenv/config';
import express from 'express';
import dns from 'node:dns/promises';
import net from 'node:net';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import * as cheerio from 'cheerio';
import {GoogleGenAI} from '@google/genai';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = Number(process.env.PORT || 3000);
const IS_PROD = process.env.NODE_ENV === 'production';

const TRACKER_PATTERNS = [
  'doubleclick.net',
  'googlesyndication.com',
  'googletagmanager.com',
  'google-analytics.com',
  'connect.facebook.net',
  'facebook.com/tr',
  'adservice.google',
  'adnxs.com',
  'scorecardresearch.com',
  'taboola.com',
  'outbrain.com',
  'hotjar.com',
  'mixpanel.com',
  'segment.io',
  'amplitude.com',
];

function isPrivateIp(ip: string): boolean {
  const normalized = ip.toLowerCase();
  if (normalized.startsWith('::ffff:')) {
    return isPrivateIp(normalized.slice(7));
  }
  const version = net.isIP(normalized);
  if (version === 4) {
    const parts = normalized.split('.').map(Number);
    if (parts.length !== 4 || parts.some((n) => Number.isNaN(n))) return true;
    const [a, b] = parts;
    if (a === 0 || a === 10 || a === 127) return true;
    if (a === 169 && b === 254) return true;
    if (a === 172 && b >= 16 && b <= 31) return true;
    if (a === 192 && b === 168) return true;
    if (a === 100 && b >= 64 && b <= 127) return true;
    if (a >= 224) return true;
    return false;
  }
  if (version === 6) {
    if (normalized === '::' || normalized === '::1') return true;
    if (normalized.startsWith('fc') || normalized.startsWith('fd')) return true;
    if (normalized.startsWith('fe80')) return true;
    return false;
  }
  return true;
}

async function assertSafeExternalUrl(rawUrl: string): Promise<URL> {
  let parsed: URL;
  try {
    parsed = new URL(rawUrl);
  } catch {
    throw new Error('Invalid URL format.');
  }

  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw new Error('Only http and https URLs are supported.');
  }
  if (parsed.username || parsed.password) {
    throw new Error('URLs with embedded credentials are not allowed.');
  }
  if (parsed.port && parsed.port !== '80' && parsed.port !== '443') {
    throw new Error('Only standard HTTP/HTTPS ports are allowed.');
  }

  const host = parsed.hostname.toLowerCase();
  if (
    host === 'localhost' ||
    host.endsWith('.localhost') ||
    host.endsWith('.local') ||
    host.endsWith('.internal')
  ) {
    throw new Error('Local or internal hostnames are blocked.');
  }

  if (net.isIP(host)) {
    if (isPrivateIp(host)) {
      throw new Error('Private or loopback IP addresses are blocked.');
    }
    return parsed;
  }

  const records = await dns.lookup(host, {all: true});
  if (!records.length || records.some((r) => isPrivateIp(r.address))) {
    throw new Error('Target domain resolves to a restricted network address.');
  }
  return parsed;
}

async function fetchWithSafeRedirects(
  initialUrl: string,
  userAgent: string,
): Promise<{finalUrl: string; html: string; contentType: string}> {
  let currentUrl = initialUrl;
  for (let hop = 0; hop < 5; hop++) {
    const safeUrl = await assertSafeExternalUrl(currentUrl);
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 12_000);

    try {
      const response = await fetch(safeUrl.toString(), {
        method: 'GET',
        redirect: 'manual',
        signal: controller.signal,
        headers: {
          'User-Agent': userAgent,
          Accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
          'Accept-Language': 'en-US,en;q=0.9',
        },
      });

      if (response.status >= 300 && response.status < 400) {
        const location = response.headers.get('location');
        if (!location) {
          throw new Error(`Redirect (${response.status}) missing Location header.`);
        }
        currentUrl = new URL(location, safeUrl).toString();
        continue;
      }

      if (!response.ok) {
        throw new Error(`Upstream site returned HTTP ${response.status}.`);
      }

      const contentType = response.headers.get('content-type') || 'text/html';
      const reader = response.body?.getReader();
      if (!reader) {
        return {finalUrl: safeUrl.toString(), html: '', contentType};
      }

      const chunks: Uint8Array[] = [];
      let totalBytes = 0;
      const MAX_BYTES = 2_500_000;
      while (true) {
        const {done, value} = await reader.read();
        if (done) break;
        if (value) {
          totalBytes += value.byteLength;
          if (totalBytes > MAX_BYTES) {
            await reader.cancel();
            break;
          }
          chunks.push(value);
        }
      }

      const merged = new Uint8Array(totalBytes);
      let offset = 0;
      for (const chunk of chunks) {
        merged.set(chunk, offset);
        offset += chunk.byteLength;
      }
      const html = new TextDecoder('utf-8', {fatal: false}).decode(merged);
      return {finalUrl: safeUrl.toString(), html, contentType};
    } finally {
      clearTimeout(timeout);
    }
  }
  throw new Error('Too many redirects.');
}

function resolveSafeHttpUrl(candidate: string | undefined, baseUrl: string): string | null {
  if (!candidate) return null;
  const trimmed = candidate.trim();
  if (!trimmed || trimmed.startsWith('#')) return null;
  if (/^(javascript|data|vbscript|file):/i.test(trimmed)) return null;
  try {
    const resolved = new URL(trimmed, baseUrl);
    if (resolved.protocol !== 'http:' && resolved.protocol !== 'https:') return null;
    return resolved.toString();
  } catch {
    return null;
  }
}

interface ParsedInlineSegment {
  text: string;
  href?: string;
}

interface ParsedPageBlock {
  type: 'heading' | 'paragraph' | 'list' | 'blockquote' | 'code' | 'image';
  level?: number;
  text?: string;
  segments?: ParsedInlineSegment[];
  items?: ParsedInlineSegment[][];
  src?: string;
  alt?: string;
}

function extractInlineSegments(
  $: cheerio.CheerioAPI,
  el: cheerio.Cheerio<any>,
  baseUrl: string,
): ParsedInlineSegment[] {
  const segments: ParsedInlineSegment[] = [];
  el.contents().each((_, node) => {
    if (node.type === 'text') {
      const text = (node.data || '').replace(/\s+/g, ' ');
      if (text.trim()) segments.push({text});
      return;
    }
    if (node.type === 'tag') {
      const $node = $(node);
      if (node.name === 'a') {
        const label = $node.text().replace(/\s+/g, ' ').trim();
        const href = resolveSafeHttpUrl($node.attr('href'), baseUrl);
        if (label) {
          segments.push({text: label, href: href || undefined});
        }
      } else {
        const text = $node.text().replace(/\s+/g, ' ').trim();
        if (text) segments.push({text: ` ${text} `});
      }
    }
  });
  return segments;
}

function parseWebpage(html: string, finalUrl: string, adBlockEnabled: boolean) {
  const $ = cheerio.load(html);
  let blockedCount = 0;

  if (adBlockEnabled) {
    $('script, iframe, ins.adsbygoogle, [class*="ad-banner"], [id*="ad-slot"]').each((_, el) => {
      const src = $(el).attr('src') || $(el).attr('data-src') || '';
      const isTracker = TRACKER_PATTERNS.some((pattern) => src.toLowerCase().includes(pattern));
      if (isTracker || el.tagName === 'script' || el.tagName === 'ins') {
        blockedCount++;
      }
      $(el).remove();
    });
  } else {
    $('script, iframe').remove();
  }

  $('style, noscript, svg[aria-hidden="true"], link[rel="preload"]').remove();

  const title =
    $('meta[property="og:title"]').attr('content')?.trim() ||
    $('title').first().text().trim() ||
    new URL(finalUrl).hostname;

  const description =
    $('meta[name="description"]').attr('content')?.trim() ||
    $('meta[property="og:description"]').attr('content')?.trim() ||
    '';

  const rawFavicon =
    $('link[rel="icon"]').attr('href') ||
    $('link[rel="shortcut icon"]').attr('href') ||
    '/favicon.ico';
  const favicon = resolveSafeHttpUrl(rawFavicon, finalUrl);

  // Extract top navigation links for quick site navigation
  const navLinks: {label: string; url: string}[] = [];
  const seenNav = new Set<string>();
  $('header a, nav a').each((_, el) => {
    if (navLinks.length >= 12) return;
    const label = $(el).text().replace(/\s+/g, ' ').trim();
    const url = resolveSafeHttpUrl($(el).attr('href'), finalUrl);
    if (label && label.length <= 32 && url && !seenNav.has(url)) {
      seenNav.add(url);
      navLinks.push({label, url});
    }
  });

  // Choose best main content root
  const contentRoot = $('main, article, [role="main"]').first().length
    ? $('main, article, [role="main"]').first()
    : $('body');

  const blocks: ParsedPageBlock[] = [];
  const seenImages = new Set<string>();

  contentRoot
    .find('h1, h2, h3, h4, p, ul, ol, blockquote, pre, img')
    .each((_, el) => {
      if (blocks.length >= 140) return;
      const tag = el.tagName?.toLowerCase();
      const $el = $(el);

      if (tag === 'h1' || tag === 'h2' || tag === 'h3' || tag === 'h4') {
        const text = $el.text().replace(/\s+/g, ' ').trim();
        if (text) {
          blocks.push({type: 'heading', level: Number(tag[1]), text});
        }
      } else if (tag === 'p') {
        if ($el.parents('li, blockquote').length > 0) return;
        const segments = extractInlineSegments($, $el, finalUrl);
        const fullText = segments.map((s) => s.text).join('').trim();
        if (fullText.length > 0) {
          blocks.push({type: 'paragraph', segments, text: fullText});
        }
      } else if (tag === 'ul' || tag === 'ol') {
        if ($el.parents('ul, ol, nav').length > 0) return;
        const items: ParsedInlineSegment[][] = [];
        $el.children('li').each((__, li) => {
          if (items.length >= 25) return;
          const segs = extractInlineSegments($, $(li), finalUrl);
          if (segs.map((s) => s.text).join('').trim()) {
            items.push(segs);
          }
        });
        if (items.length > 0) {
          blocks.push({type: 'list', items});
        }
      } else if (tag === 'blockquote') {
        const text = $el.text().replace(/\s+/g, ' ').trim();
        if (text) blocks.push({type: 'blockquote', text});
      } else if (tag === 'pre') {
        const text = $el.text().trim();
        if (text) blocks.push({type: 'code', text: text.slice(0, 2000)});
      } else if (tag === 'img') {
        const src = resolveSafeHttpUrl($el.attr('src') || $el.attr('data-src'), finalUrl);
        const alt = ($el.attr('alt') || '').trim();
        const width = Number($el.attr('width') || 0);
        if (src && !seenImages.has(src) && (width === 0 || width >= 48)) {
          seenImages.add(src);
          blocks.push({type: 'image', src, alt});
        }
      }
    });

  // Fallback links if page had sparse blocks
  const pageLinks: {label: string; url: string}[] = [];
  const seenLinks = new Set<string>();
  $('a[href]').each((_, el) => {
    if (pageLinks.length >= 24) return;
    const label = $(el).text().replace(/\s+/g, ' ').trim();
    const url = resolveSafeHttpUrl($(el).attr('href'), finalUrl);
    if (label && label.length >= 3 && label.length <= 90 && url && !seenLinks.has(url)) {
      seenLinks.add(url);
      pageLinks.push({label, url});
    }
  });

  const textContent = contentRoot.text().replace(/\s+/g, ' ').trim().slice(0, 15_000);
  const parsedUrl = new URL(finalUrl);

  return {
    finalUrl,
    domain: parsedUrl.hostname,
    isSecure: parsedUrl.protocol === 'https:',
    title,
    description,
    favicon,
    blockedCount,
    navLinks,
    blocks,
    pageLinks,
    textContent,
  };
}

function getGeminiClient(): GoogleGenAI {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    throw new Error('GEMINI_API_KEY is not configured. Add it in Settings -> Secrets.');
  }
  return new GoogleGenAI({
    apiKey,
    httpOptions: {
      headers: {
        'User-Agent': 'aistudio-build',
      },
    },
  });
}

async function startServer() {
  const app = express();
  app.use(express.json({limit: '1mb'}));

  app.get('/api/health', (_req, res) => {
    res.json({
      ok: true,
      hasGeminiKey: Boolean(process.env.GEMINI_API_KEY),
    });
  });

  // Live webpage fetcher with SSRF protection & tracker stripping
  app.post('/api/browse', async (req, res) => {
    try {
      const rawUrl = typeof req.body?.url === 'string' ? req.body.url.trim() : '';
      const adBlockEnabled = req.body?.adBlockEnabled !== false;
      const desktopMode = Boolean(req.body?.desktopMode);

      if (!rawUrl) {
        return res.status(400).json({error: 'URL is required.'});
      }

      const normalizedUrl = /^https?:\/\//i.test(rawUrl) ? rawUrl : `https://${rawUrl}`;
      const ua = desktopMode
        ? 'Mozilla/5.0 (X11; Linux x86_64; rv:159.0) Gecko/20100101 Firefox/159.0'
        : 'Mozilla/5.0 (Android 14; Mobile; rv:159.0) Gecko/159.0 Firefox/159.0';

      const {finalUrl, html, contentType} = await fetchWithSafeRedirects(normalizedUrl, ua);
      if (!contentType.includes('html') && !contentType.includes('xml') && !contentType.includes('text')) {
        return res.status(415).json({error: `Unsupported content type: ${contentType}`});
      }

      const parsed = parseWebpage(html, finalUrl, adBlockEnabled);
      return res.json(parsed);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to load webpage.';
      return res.status(400).json({error: message});
    }
  });

  // Live web search via DuckDuckGo HTML
  app.post('/api/search', async (req, res) => {
    try {
      const query = typeof req.body?.query === 'string' ? req.body.query.trim() : '';
      if (!query) {
        return res.status(400).json({error: 'Search query is required.'});
      }

      const searchUrl = `https://html.duckduckgo.com/html/?q=${encodeURIComponent(query)}`;
      const {html} = await fetchWithSafeRedirects(
        searchUrl,
        'Mozilla/5.0 (Android 14; Mobile; rv:159.0) Gecko/159.0 Firefox/159.0',
      );

      const $ = cheerio.load(html);
      const results: {title: string; url: string; domain: string; snippet: string}[] = [];

      $('.result').each((_, el) => {
        if (results.length >= 12) return;
        const linkEl = $(el).find('a.result__a').first();
        const title = linkEl.text().replace(/\s+/g, ' ').trim();
        let href = linkEl.attr('href') || '';

        if (href.includes('uddg=')) {
          try {
            const u = new URL(href, 'https://duckduckgo.com');
            const decoded = u.searchParams.get('uddg');
            if (decoded) href = decoded;
          } catch {
            // keep original href
          }
        }

        const safeHref = resolveSafeHttpUrl(href, 'https://duckduckgo.com');
        const snippet = $(el).find('.result__snippet').text().replace(/\s+/g, ' ').trim();
        if (title && safeHref) {
          results.push({
            title,
            url: safeHref,
            domain: new URL(safeHref).hostname,
            snippet,
          });
        }
      });

      return res.json({query, results});
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Search failed.';
      return res.status(502).json({error: message});
    }
  });

  // Contextual Gemini AI endpoint (grounding in active webpage or Google Search)
  app.post('/api/ai', async (req, res) => {
    try {
      const prompt = typeof req.body?.prompt === 'string' ? req.body.prompt.trim() : '';
      const pageContext = req.body?.pageContext as
        | {url?: string; title?: string; textContent?: string}
        | undefined;
      const useWebGrounding = Boolean(req.body?.useWebGrounding);

      if (!prompt) {
        return res.status(400).json({error: 'Prompt is required.'});
      }

      const ai = getGeminiClient();
      const hasPage = Boolean(pageContext?.url && pageContext?.textContent);

      const fullPrompt = hasPage
        ? `Active Browser Tab:\nTitle: ${pageContext?.title || 'Untitled'}\nURL: ${pageContext?.url}\n\nExtracted Page Text:\n${(pageContext?.textContent || '').slice(0, 12_000)}\n\nUser Request: ${prompt}`
        : prompt;

      let response: Awaited<ReturnType<typeof ai.models.generateContent>> | null = null;
      let lastErr: unknown;
      const delays = [0, 600, 1400];
      for (let attempt = 0; attempt < delays.length; attempt++) {
        if (delays[attempt] > 0) {
          await new Promise((r) => setTimeout(r, delays[attempt]));
        }
        try {
          response = await ai.models.generateContent({
            model: 'gemini-4-hydrogen',
            contents: fullPrompt,
            config: {
              systemInstruction:
                'You are SAB AI, the built-in assistant inside Simple AI Browser. Give crisp, direct, accurate answers formatted cleanly in concise paragraphs and bullet points. When analyzing an active webpage, stay grounded in the provided page content and cite key details clearly.',
              ...(useWebGrounding && !hasPage ? {tools: [{googleSearch: {}}]} : {}),
            },
          });
          break;
        } catch (err: any) {
          lastErr = err;
          const code = Number(err?.status || 0);
          if (code !== 503 && code !== 429) {
            throw err;
          }
        }
      }
      if (!response) throw lastErr;

      const groundingChunks = response.candidates?.[0]?.groundingMetadata?.groundingChunks || [];
      const sources = groundingChunks
        .map((c: any) => c?.web)
        .filter((w: any) => w?.uri && w?.title)
        .slice(0, 6);

      return res.json({
        text: response.text || 'No response generated.',
        sources,
      });
    } catch (error: any) {
      const status = typeof error?.status === 'number' ? error.status : 500;
      let message =
        error instanceof Error ? error.message : 'Unable to reach Gemini AI service.';
      try {
        const parsed = JSON.parse(message);
        if (parsed?.error?.message) {
          message = parsed.error.message;
        }
      } catch {
        // keep plain message
      }
      return res.status(status >= 400 && status < 600 ? status : 500).json({error: message});
    }
  });

  if (!IS_PROD) {
    const {createServer: createViteServer} = await import('vite');
    const vite = await createViteServer({
      server: {middlewareMode: true},
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distDir = path.resolve(__dirname, 'dist');
    app.use(express.static(distDir));
    app.get('*', (_req, res) => {
      res.sendFile(path.join(distDir, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`Simple AI Browser (SAB) running at http://localhost:${PORT}`);
  });
}

startServer().catch((err) => {
  console.error('Failed to start server:', err);
  process.exit(1);
});
