// Built-in GeckoView content script for extracting readable page context for SAB AI
(() => {
  if (typeof browser === 'undefined' || !browser.runtime?.connectNative) return;

  const port = browser.runtime.connectNative('sabBridge');

  port.onMessage.addListener((message) => {
    if (message?.type === 'EXTRACT_CONTEXT') {
      const maxChars = typeof message.maxChars === 'number' ? message.maxChars : 12000;
      const selection = window.getSelection()?.toString()?.trim() || null;
      const bodyText = (document.body?.innerText || '').replace(/\s+/g, ' ').trim();
      port.postMessage({
        type: 'PAGE_CONTEXT',
        url: window.location.href,
        title: document.title,
        selectedText: selection,
        textContent: bodyText.slice(0, maxChars),
      });
    }
  });
})();
