(() => {
  const privacyState = {
    doNotTrack: true,
    globalPrivacyControl: true
  };

  let bgPort = null;
  let reconnectTimer = null;

  function connectBackgroundPort(attempt = 0) {
    if (typeof browser === "undefined" || !browser.runtime || !browser.runtime.connectNative) {
      return;
    }
    try {
      const nextPort = browser.runtime.connectNative("sabBridge");
      bgPort = nextPort;
      nextPort.onMessage.addListener((msg) => {
        if (!msg || typeof msg !== "object") return;
        if (msg.type === "PRIVACY_CONFIG") {
          privacyState.doNotTrack = Boolean(msg.doNotTrack);
          privacyState.globalPrivacyControl = Boolean(msg.globalPrivacyControl);
        }
      });
      nextPort.onDisconnect.addListener(() => {
        if (bgPort === nextPort) bgPort = null;
        if (attempt < 5 && !reconnectTimer) {
          reconnectTimer = setTimeout(() => {
            reconnectTimer = null;
            connectBackgroundPort(attempt + 1);
          }, 500 * (attempt + 1));
        }
      });
    } catch (_) {
      if (attempt < 5 && !reconnectTimer) {
        reconnectTimer = setTimeout(() => {
          reconnectTimer = null;
          connectBackgroundPort(attempt + 1);
        }, 500 * (attempt + 1));
      }
    }
  }

  connectBackgroundPort(0);

  if (typeof browser !== "undefined" && browser.webRequest && browser.webRequest.onBeforeSendHeaders) {
    browser.webRequest.onBeforeSendHeaders.addListener(
      (details) => {
        const headers = (details.requestHeaders || []).filter((h) => {
          const lower = (h.name || "").toLowerCase();
          return lower !== "dnt" && lower !== "sec-gpc";
        });
        if (privacyState.doNotTrack) {
          headers.push({ name: "DNT", value: "1" });
        }
        if (privacyState.globalPrivacyControl) {
          headers.push({ name: "Sec-GPC", value: "1" });
        }
        return { requestHeaders: headers };
      },
      { urls: ["<all_urls>"] },
      ["blocking", "requestHeaders"]
    );
  }
})();
