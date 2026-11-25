(() => {
  const DARK_STYLE_ID = "sab-force-dark-css";
  const READER_STYLE_ID = "sab-reader-mode-css";

  function setForceDark(enabled) {
    let style = document.getElementById(DARK_STYLE_ID);
    if (!enabled) {
      if (style) style.remove();
      return;
    }
    if (!style) {
      style = document.createElement("style");
      style.id = DARK_STYLE_ID;
      style.textContent = `
        html {
          filter: invert(0.92) hue-rotate(180deg) !important;
          background-color: #0b0d14 !important;
        }
        img, video, picture, canvas, svg, iframe {
          filter: invert(1) hue-rotate(180deg) !important;
        }
      `;
      (document.head || document.documentElement).appendChild(style);
    }
  }

  function setReaderMode(enabled) {
    let style = document.getElementById(READER_STYLE_ID);
    if (!enabled) {
      if (style) style.remove();
      return;
    }
    if (!style) {
      style = document.createElement("style");
      style.id = READER_STYLE_ID;
      style.textContent = `
        body {
          max-width: 720px !important;
          margin: 0 auto !important;
          padding: 20px 18px 80px 18px !important;
          line-height: 1.72 !important;
          font-size: 18px !important;
          letter-spacing: 0.01em !important;
        }
        aside, nav, footer, [role="banner"], [role="complementary"], .ad, .ads, .advertisement {
          display: none !important;
        }
      `;
      (document.head || document.documentElement).appendChild(style);
    }
  }

  function extractSnapshot(maxChars) {
    const selection = window.getSelection ? window.getSelection().toString().trim() : "";
    const text = (document.body ? document.body.innerText : "")
      .replace(/\s+/g, " ")
      .trim()
      .slice(0, maxChars || 12000);
    return {
      url: window.location.href,
      title: document.title || window.location.hostname,
      textContent: text,
      selectedText: selection || null
    };
  }

  if (typeof browser !== "undefined" && browser.runtime && browser.runtime.connectNative) {
    try {
      const port = browser.runtime.connectNative("sabBridge");
      port.onMessage.addListener((msg) => {
        if (!msg || !msg.type) return;
        if (msg.type === "SET_FORCE_DARK") {
          setForceDark(Boolean(msg.enabled));
        } else if (msg.type === "SET_READER_MODE") {
          setReaderMode(Boolean(msg.enabled));
        } else if (msg.type === "EXTRACT_SNAPSHOT") {
          port.postMessage({
            type: "SNAPSHOT_RESULT",
            requestId: msg.requestId,
            payload: extractSnapshot(msg.maxChars)
          });
        }
      });
    } catch (_) {
      // Native messaging bridge initializes when GeckoSession attaches
    }
  }
})();
