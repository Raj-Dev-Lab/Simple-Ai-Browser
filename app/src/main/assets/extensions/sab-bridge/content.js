(() => {
  if (window !== window.top) return;

  const DARK_STYLE_ID = "sab-force-dark-css";
  const ZOOM_STYLE_ID = "sab-text-zoom-css";
  const READER_STYLE_ID = "sab-reader-mode-css";
  const READER_OVERLAY_ID = "sab-reader-overlay";

  const state = {
    forceDark: false,
    readerMode: false,
    textZoomPercent: 100,
    doNotTrack: true,
    globalPrivacyControl: true
  };

  let port = null;
  let reconnectTimer = null;
  let prevBodyOverflow = "";

  function ensureStyle(id) {
    let el = document.getElementById(id);
    if (!el) {
      el = document.createElement("style");
      el.id = id;
      const parent = document.head || document.documentElement;
      if (parent) {
        parent.appendChild(el);
      }
    }
    return el;
  }

  function removeElementById(id) {
    const el = document.getElementById(id);
    if (el && el.parentNode) {
      el.parentNode.removeChild(el);
    }
  }

  function parseRgbLuminance(colorStr) {
    if (!colorStr) return null;
    const match = colorStr.match(/rgba?\(\s*(\d+),\s*(\d+),\s*(\d+)(?:,\s*([0-9.]+))?\s*\)/i);
    if (!match) return null;
    const alpha = match[4] !== undefined ? parseFloat(match[4]) : 1;
    if (alpha < 0.15) return null;
    const r = Number(match[1]) / 255;
    const g = Number(match[2]) / 255;
    const b = Number(match[3]) / 255;
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  }

  function isPageNativelyDark() {
    try {
      const rootStyle = document.documentElement ? getComputedStyle(document.documentElement) : null;
      const bodyStyle = document.body ? getComputedStyle(document.body) : null;
      const bodyLum = bodyStyle ? parseRgbLuminance(bodyStyle.backgroundColor) : null;
      const rootLum = rootStyle ? parseRgbLuminance(rootStyle.backgroundColor) : null;
      if (bodyLum !== null) return bodyLum < 0.28;
      if (rootLum !== null) return rootLum < 0.28;
      const colorSchemeMeta = document.querySelector('meta[name="color-scheme"]');
      if (colorSchemeMeta && /dark/i.test(colorSchemeMeta.getAttribute("content") || "")) {
        const txtLum = bodyStyle ? parseRgbLuminance(bodyStyle.color) : null;
        if (txtLum !== null && txtLum > 0.65) return true;
      }
    } catch (_) {
      // Fallback to light page assumption
    }
    return false;
  }

  function applyForceDark() {
    if (!state.forceDark || state.readerMode) {
      removeElementById(DARK_STYLE_ID);
      return;
    }
    const style = ensureStyle(DARK_STYLE_ID);
    if (isPageNativelyDark()) {
      style.textContent = `
        :root {
          color-scheme: dark !important;
        }
      `;
    } else {
      style.textContent = `
        :root {
          color-scheme: dark !important;
          filter: invert(0.91) hue-rotate(180deg) !important;
          background-color: #0b0d14 !important;
        }
        img, video, picture, canvas, iframe, embed, object,
        svg:not(.sab-invert-ok),
        [style*="background-image"]:not(#sab-reader-overlay *) {
          filter: invert(1) hue-rotate(180deg) !important;
        }
        #${READER_OVERLAY_ID} {
          filter: invert(1) hue-rotate(180deg) !important;
        }
      `;
    }
  }

  function applyTextZoom() {
    const clamped = Math.max(75, Math.min(160, Number(state.textZoomPercent) || 100));
    state.textZoomPercent = clamped;

    const overlay = document.getElementById(READER_OVERLAY_ID);
    if (overlay) {
      overlay.style.setProperty("--sab-reader-font-px", `${(18 * clamped / 100).toFixed(1)}px`);
      removeElementById(ZOOM_STYLE_ID);
      return;
    }

    if (clamped === 100) {
      removeElementById(ZOOM_STYLE_ID);
      return;
    }
    const factor = (clamped / 100).toFixed(2);
    const style = ensureStyle(ZOOM_STYLE_ID);
    style.textContent = `
      html {
        zoom: ${factor} !important;
        -webkit-text-size-adjust: ${clamped}% !important;
        text-size-adjust: ${clamped}% !important;
      }
    `;
  }

  function scoreCandidate(el) {
    if (!el || !el.textContent) return -1;
    const tag = (el.tagName || "").toLowerCase();
    if (tag === "nav" || tag === "aside" || tag === "footer" || tag === "header") return -1;
    const clsId = `${el.className || ""} ${el.id || ""}`.toLowerCase();
    if (/(comment|sidebar|footer|nav|menu|cookie|banner|popup|promo|related|recommend)/.test(clsId)) {
      return -1;
    }
    const paragraphs = el.querySelectorAll ? el.querySelectorAll("p") : [];
    let pChars = 0;
    for (let i = 0; i < paragraphs.length; i++) {
      const txt = (paragraphs[i].textContent || "").trim();
      if (txt.length >= 25) pChars += txt.length;
    }
    const totalLen = (el.textContent || "").trim().length;
    const linkChars = Array.from(el.querySelectorAll ? el.querySelectorAll("a") : [])
      .reduce((acc, a) => acc + (a.textContent || "").length, 0);
    const linkDensity = totalLen > 0 ? linkChars / totalLen : 1;
    if (linkDensity > 0.55) return 0;

    let bonus = 1;
    if (tag === "article" || el.getAttribute("itemprop") === "articleBody") bonus = 2.2;
    else if (tag === "main" || el.getAttribute("role") === "main") bonus = 1.6;
    return (pChars + totalLen * 0.15) * bonus;
  }

  function findBestArticleNode() {
    const selectors = [
      "[itemprop='articleBody']",
      "article",
      "main",
      "[role='main']",
      ".post-content",
      ".article-content",
      ".entry-content"
    ];
    let bestEl = null;
    let bestScore = 180;

    for (const sel of selectors) {
      const nodes = document.querySelectorAll(sel);
      for (const node of nodes) {
        const sc = scoreCandidate(node);
        if (sc > bestScore) {
          bestScore = sc;
          bestEl = node;
        }
      }
    }

    if (!bestEl) {
      const divs = document.querySelectorAll("div, section");
      for (let i = 0; i < Math.min(divs.length, 150); i++) {
        const sc = scoreCandidate(divs[i]);
        if (sc > bestScore) {
          bestScore = sc;
          bestEl = divs[i];
        }
      }
    }
    return bestEl || document.body;
  }

  function sanitizeNodeInto(src, dest) {
    const allowedTags = new Set([
      "h1", "h2", "h3", "h4", "p", "ul", "ol", "li", "blockquote",
      "pre", "code", "figure", "figcaption", "img", "a", "strong", "b", "em", "i", "br", "hr"
    ]);
    const blockedSelector = "script, style, noscript, iframe, nav, aside, footer, form, button, svg, [aria-hidden='true'], .ad, .ads, .advertisement";

    const clone = src.cloneNode(true);
    if (clone.querySelectorAll) {
      clone.querySelectorAll(blockedSelector).forEach((n) => n.remove());
    }

    const blocks = clone.querySelectorAll
      ? clone.querySelectorAll("h1, h2, h3, h4, p, ul, ol, blockquote, pre, figure")
      : [];

    if (!blocks || blocks.length === 0) {
      const text = (clone.innerText || clone.textContent || "").trim();
      text.split(/\n{2,}/).forEach((chunk) => {
        const clean = chunk.replace(/\s+/g, " ").trim();
        if (clean.length > 0) {
          const p = document.createElement("p");
          p.textContent = clean;
          dest.appendChild(p);
        }
      });
      return;
    }

    const seen = new Set();
    blocks.forEach((node) => {
      // Skip nested blocks whose ancestor is already being appended (e.g. p inside blockquote or li)
      let parent = node.parentElement;
      while (parent && parent !== clone) {
        if (seen.has(parent)) return;
        parent = parent.parentElement;
      }
      const tag = (node.tagName || "").toLowerCase();
      if (!allowedTags.has(tag)) return;

      if (tag === "p" || tag === "blockquote") {
        const txt = (node.textContent || "").trim();
        if (txt.length < 3) return;
      }

      const cleanNode = document.createElement(tag);
      if (tag === "figure") {
        const img = node.querySelector("img");
        if (img && (img.currentSrc || img.getAttribute("src"))) {
          const cleanImg = document.createElement("img");
          cleanImg.src = img.currentSrc || img.getAttribute("src");
          cleanImg.alt = img.getAttribute("alt") || "";
          cleanNode.appendChild(cleanImg);
        }
        const cap = node.querySelector("figcaption");
        if (cap && cap.textContent.trim()) {
          const cleanCap = document.createElement("figcaption");
          cleanCap.textContent = cap.textContent.trim();
          cleanNode.appendChild(cleanCap);
        }
        if (cleanNode.childNodes.length > 0) {
          seen.add(node);
          dest.appendChild(cleanNode);
        }
        return;
      }

      // Preserve inline formatting text safely without raw scripts or inline styles
      cleanNode.textContent = (node.textContent || "").replace(/\s+/g, " ").trim();
      if (tag === "pre") {
        cleanNode.textContent = (node.textContent || "").trim();
      } else if (tag === "ul" || tag === "ol") {
        cleanNode.textContent = "";
        node.querySelectorAll(":scope > li").forEach((li) => {
          const liTxt = (li.textContent || "").replace(/\s+/g, " ").trim();
          if (liTxt) {
            const cleanLi = document.createElement("li");
            cleanLi.textContent = liTxt;
            cleanNode.appendChild(cleanLi);
          }
        });
        if (!cleanNode.hasChildNodes()) return;
      }

      if (cleanNode.textContent.length > 0 || cleanNode.hasChildNodes()) {
        seen.add(node);
        dest.appendChild(cleanNode);
      }
    });
  }

  function applyReaderMode() {
    if (!state.readerMode) {
      removeElementById(READER_OVERLAY_ID);
      removeElementById(READER_STYLE_ID);
      if (document.body) {
        document.body.style.overflow = prevBodyOverflow || "";
      }
      applyForceDark();
      applyTextZoom();
      return;
    }

    if (!document.body) return;

    // Remove Force Dark root filter when rendering native dark/light Reader Mode
    removeElementById(DARK_STYLE_ID);

    const useDarkReader = state.forceDark || isPageNativelyDark() ||
      (window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches);

    const style = ensureStyle(READER_STYLE_ID);
    const fontPx = (18 * (state.textZoomPercent || 100) / 100).toFixed(1);
    style.textContent = `
      #${READER_OVERLAY_ID} {
        position: fixed !important;
        inset: 0 !important;
        z-index: 2147483646 !important;
        overflow-y: auto !important;
        -webkit-overflow-scrolling: touch !important;
        background: ${useDarkReader ? "#0E1118" : "#FAF8F4"} !important;
        color: ${useDarkReader ? "#E6EAF2" : "#1F242D"} !important;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Georgia, serif !important;
        font-size: var(--sab-reader-font-px, ${fontPx}px) !important;
        line-height: 1.72 !important;
        margin: 0 !important;
        padding: 24px 18px 96px 18px !important;
        box-sizing: border-box !important;
      }
      #${READER_OVERLAY_ID} .sab-reader-container {
        max-width: 700px !important;
        margin: 0 auto !important;
      }
      #${READER_OVERLAY_ID} .sab-reader-meta {
        font-size: 0.78em !important;
        letter-spacing: 0.04em !important;
        text-transform: uppercase !important;
        color: ${useDarkReader ? "#94A3B8" : "#64748B"} !important;
        margin-bottom: 10px !important;
      }
      #${READER_OVERLAY_ID} h1.sab-reader-title {
        font-size: 1.65em !important;
        line-height: 1.24 !important;
        font-weight: 700 !important;
        margin: 0 0 18px 0 !important;
        color: ${useDarkReader ? "#FFFFFF" : "#0F172A"} !important;
      }
      #${READER_OVERLAY_ID} h2, #${READER_OVERLAY_ID} h3, #${READER_OVERLAY_ID} h4 {
        line-height: 1.3 !important;
        margin: 1.35em 0 0.55em 0 !important;
        color: ${useDarkReader ? "#F8FAFC" : "#111827"} !important;
      }
      #${READER_OVERLAY_ID} p {
        margin: 0 0 1.05em 0 !important;
      }
      #${READER_OVERLAY_ID} blockquote {
        margin: 1.2em 0 !important;
        padding: 0.4em 0 0.4em 1em !important;
        border-left: 3px solid #FF2E54 !important;
        color: ${useDarkReader ? "#CBD5E1" : "#475569"} !important;
      }
      #${READER_OVERLAY_ID} pre {
        overflow-x: auto !important;
        padding: 12px !important;
        border-radius: 10px !important;
        background: ${useDarkReader ? "#181E2D" : "#EFECE6"} !important;
        font-size: 0.85em !important;
      }
      #${READER_OVERLAY_ID} img {
        max-width: 100% !important;
        height: auto !important;
        border-radius: 12px !important;
        margin: 14px 0 !important;
        display: block !important;
      }
      #${READER_OVERLAY_ID} ul, #${READER_OVERLAY_ID} ol {
        padding-left: 1.35em !important;
        margin: 0 0 1.1em 0 !important;
      }
      #${READER_OVERLAY_ID} li {
        margin-bottom: 0.45em !important;
      }
      #${READER_OVERLAY_ID} figcaption {
        font-size: 0.8em !important;
        color: ${useDarkReader ? "#94A3B8" : "#64748B"} !important;
        margin-top: -8px !important;
        margin-bottom: 14px !important;
      }
      #${READER_OVERLAY_ID} .sab-reader-divider {
        height: 1px !important;
        background: ${useDarkReader ? "#262E42" : "#E2DDD4"} !important;
        margin: 16px 0 22px 0 !important;
      }
    `;

    let overlay = document.getElementById(READER_OVERLAY_ID);
    if (!overlay) {
      prevBodyOverflow = document.body.style.overflow || "";
      document.body.style.overflow = "hidden";

      overlay = document.createElement("div");
      overlay.id = READER_OVERLAY_ID;
      overlay.setAttribute("role", "article");

      const container = document.createElement("div");
      container.className = "sab-reader-container";

      const sourceNode = findBestArticleNode();
      const rawWords = ((sourceNode.textContent || "").trim().match(/\S+/g) || []).length;
      const readMinutes = Math.max(1, Math.round(rawWords / 220));

      const meta = document.createElement("div");
      meta.className = "sab-reader-meta";
      meta.textContent = `${window.location.hostname.replace(/^www\./, "")} • ${readMinutes} min read`;
      container.appendChild(meta);

      const h1 = document.createElement("h1");
      h1.className = "sab-reader-title";
      const pageH1 = document.querySelector("h1");
      h1.textContent = (pageH1 && pageH1.textContent.trim().length > 3)
        ? pageH1.textContent.trim()
        : (document.title || window.location.hostname);
      container.appendChild(h1);

      const divider = document.createElement("div");
      divider.className = "sab-reader-divider";
      container.appendChild(divider);

      const bodyWrap = document.createElement("div");
      sanitizeNodeInto(sourceNode, bodyWrap);
      if (!bodyWrap.hasChildNodes()) {
        const fallbackP = document.createElement("p");
        fallbackP.textContent = (document.body.innerText || "").trim() || "No readable article text found on this page.";
        bodyWrap.appendChild(fallbackP);
      }
      container.appendChild(bodyWrap);
      overlay.appendChild(container);
      document.documentElement.appendChild(overlay);
    }

    applyTextZoom();
  }

  function applyNavigatorPrivacy() {
    try {
      const targetProto = Object.getPrototypeOf(window.navigator) || window.navigator;
      Object.defineProperty(targetProto, "doNotTrack", {
        get: () => (state.doNotTrack ? "1" : null),
        configurable: true
      });
      Object.defineProperty(targetProto, "globalPrivacyControl", {
        get: () => Boolean(state.globalPrivacyControl),
        configurable: true
      });
    } catch (_) {
      try {
        Object.defineProperty(window.navigator, "doNotTrack", {
          get: () => (state.doNotTrack ? "1" : null),
          configurable: true
        });
        Object.defineProperty(window.navigator, "globalPrivacyControl", {
          get: () => Boolean(state.globalPrivacyControl),
          configurable: true
        });
      } catch (_inner) {
        // Read-only navigator in hardened context
      }
    }
  }

  function applyAllModes() {
    applyNavigatorPrivacy();
    applyForceDark();
    applyReaderMode();
    applyTextZoom();
  }

  function extractSnapshot(maxChars) {
    const selection = window.getSelection ? window.getSelection().toString().trim() : "";
    const article = findBestArticleNode();
    const raw = (article && article.innerText ? article.innerText : (document.body ? document.body.innerText : ""))
      .replace(/\s+/g, " ")
      .trim()
      .slice(0, maxChars || 12000);
    return {
      url: window.location.href,
      title: document.title || window.location.hostname,
      textContent: raw,
      selectedText: selection || null
    };
  }

  function scanPageMedia() {
    const items = [];
    const seen = new Set();

    function pushItem(url, title, kind, mimeType) {
      if (!url || typeof url !== "string") return;
      const trimmed = url.trim();
      if (!trimmed || trimmed.startsWith("javascript:") || trimmed.startsWith("about:")) return;
      if (seen.has(trimmed)) return;
      seen.add(trimmed);
      items.push({
        url: trimmed,
        title: (title || "").trim() || trimmed.split("/").pop()?.split("?")[0] || trimmed,
        kind: kind || "file",
        mimeType: mimeType || ""
      });
    }

    document.querySelectorAll("video").forEach((v, idx) => {
      const src = v.currentSrc || v.src;
      if (src) pushItem(src, v.title || v.getAttribute("aria-label") || `Video ${idx + 1}`, "video", "video/mp4");
      v.querySelectorAll("source").forEach((s) => {
        if (s.src) pushItem(s.src, v.title || `Video Source ${idx + 1}`, "video", s.type || "video/mp4");
      });
    });

    document.querySelectorAll("audio").forEach((a, idx) => {
      const src = a.currentSrc || a.src;
      if (src) pushItem(src, a.title || a.getAttribute("aria-label") || `Audio ${idx + 1}`, "audio", "audio/mpeg");
      a.querySelectorAll("source").forEach((s) => {
        if (s.src) pushItem(s.src, a.title || `Audio Source ${idx + 1}`, "audio", s.type || "audio/mpeg");
      });
    });

    document.querySelectorAll("img").forEach((img, idx) => {
      const w = img.naturalWidth || img.width || 0;
      const h = img.naturalHeight || img.height || 0;
      if (w >= 140 || h >= 140) {
        const src = img.currentSrc || img.src;
        if (src && !src.startsWith("data:image/svg")) {
          pushItem(src, img.alt || img.title || `Image ${idx + 1}`, "image", "");
        }
      }
    });

    const downloadableExt = /\.(mp4|mkv|webm|mov|avi|mp3|m4a|flac|ogg|wav|pdf|zip|rar|7z|tar|gz|apk|iso|torrent)(\?|$)/i;
    document.querySelectorAll("a[href]").forEach((a) => {
      const href = a.href || a.getAttribute("href") || "";
      if (href.startsWith("magnet:")) {
        pushItem(href, a.textContent || "Magnet Link", "magnet", "application/x-bittorrent");
      } else if (downloadableExt.test(href) || a.hasAttribute("download")) {
        const lower = href.toLowerCase();
        const kind = lower.includes(".torrent") ? "torrent"
          : /\.(mp4|mkv|webm|mov|avi)(\?|$)/.test(lower) ? "video"
          : /\.(mp3|m4a|flac|ogg|wav)(\?|$)/.test(lower) ? "audio"
          : /\.(zip|rar|7z|tar|gz|iso)(\?|$)/.test(lower) ? "archive"
          : /\.(pdf)(\?|$)/.test(lower) ? "document"
          : /\.(apk)(\?|$)/.test(lower) ? "app"
          : "file";
        pushItem(href, a.textContent || a.getAttribute("download") || "", kind, "");
      }
    });

    return items.slice(0, 60);
  }

  function connectBridge(attempt = 0) {
    if (typeof browser === "undefined" || !browser.runtime || !browser.runtime.connectNative) {
      return;
    }
    try {
      const nextPort = browser.runtime.connectNative("sabBridge");
      port = nextPort;
      nextPort.onMessage.addListener((msg) => {
        if (!msg || !msg.type) return;
        if (msg.type === "SYNC_MODES") {
          state.forceDark = Boolean(msg.forceDark);
          state.readerMode = Boolean(msg.readerMode);
          state.textZoomPercent = Number(msg.textZoomPercent || msg.textZoom) || 100;
          if (msg.doNotTrack !== undefined) {
            state.doNotTrack = Boolean(msg.doNotTrack);
          }
          if (msg.globalPrivacyControl !== undefined) {
            state.globalPrivacyControl = Boolean(msg.globalPrivacyControl);
          }
          applyAllModes();
        } else if (msg.type === "PRIVACY_CONFIG") {
          state.doNotTrack = Boolean(msg.doNotTrack);
          state.globalPrivacyControl = Boolean(msg.globalPrivacyControl);
          applyNavigatorPrivacy();
        } else if (msg.type === "SET_FORCE_DARK") {
          state.forceDark = Boolean(msg.enabled);
          applyForceDark();
        } else if (msg.type === "SET_READER_MODE") {
          state.readerMode = Boolean(msg.enabled);
          applyReaderMode();
        } else if (msg.type === "SET_TEXT_ZOOM") {
          state.textZoomPercent = Number(msg.percent) || 100;
          applyTextZoom();
        } else if (msg.type === "EXTRACT_SNAPSHOT") {
          nextPort.postMessage({
            type: "SNAPSHOT_RESULT",
            requestId: msg.requestId,
            payload: extractSnapshot(msg.maxChars)
          });
        } else if (msg.type === "SCAN_PAGE_MEDIA") {
          nextPort.postMessage({
            type: "MEDIA_SCAN_RESULT",
            requestId: msg.requestId,
            items: scanPageMedia()
          });
        }
      });
      nextPort.onDisconnect.addListener(() => {
        if (port === nextPort) port = null;
        if (attempt < 4 && !reconnectTimer) {
          reconnectTimer = setTimeout(() => {
            reconnectTimer = null;
            connectBridge(attempt + 1);
          }, 350 * (attempt + 1));
        }
      });
    } catch (_) {
      if (attempt < 4 && !reconnectTimer) {
        reconnectTimer = setTimeout(() => {
          reconnectTimer = null;
          connectBridge(attempt + 1);
        }, 400 * (attempt + 1));
      }
    }
  }

  connectBridge(0);

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => {
      applyAllModes();
      if (!port) connectBridge(0);
    }, { once: true });
  }

  // Keep styles resilient on dynamic SPAs that rewrite <head>
  let observerQueued = false;
  const observer = new MutationObserver(() => {
    if (observerQueued) return;
    if (!state.forceDark && state.textZoomPercent === 100) return;
    observerQueued = true;
    requestAnimationFrame(() => {
      observerQueued = false;
      if (state.forceDark && !state.readerMode && !document.getElementById(DARK_STYLE_ID)) {
        applyForceDark();
      }
      if (state.textZoomPercent !== 100 && !state.readerMode && !document.getElementById(ZOOM_STYLE_ID)) {
        applyTextZoom();
      }
    });
  });
  if (document.documentElement) {
    observer.observe(document.documentElement, { childList: true });
  }
})();
