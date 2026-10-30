browser.runtime.sendNativeMessage("kun.browser", { action: "profile-environment" }).then((config) => {
  if (!config || config.enabled !== true) return;
  const pageConfig = JSON.stringify({
    locationMode: typeof config.locationMode === "string" ? config.locationMode : "auto",
    latitude: Number(config.latitude),
    longitude: Number(config.longitude),
  });
  const wrapped = `(() => {
    const cfg = ${pageConfig};
    const nav = window.navigator;
    if (cfg.locationMode === "off") {
      const denied = (success, failure) => { if (typeof failure === "function") setTimeout(() => failure({ code: 1, message: "Geolocation is disabled for this KUN profile." }), 0); };
      try { Object.defineProperty(nav, "geolocation", { configurable: true, value: { getCurrentPosition: denied, watchPosition: denied, clearWatch: () => {} } }); } catch (_) {}
    } else if ((cfg.locationMode === "custom" || cfg.locationMode === "resolved") && Number.isFinite(cfg.latitude) && Number.isFinite(cfg.longitude) && Math.abs(cfg.latitude) <= 90 && Math.abs(cfg.longitude) <= 180) {
      let nextWatchId = 1;
      const watchers = new Map();
      const position = () => ({ coords: { latitude: cfg.latitude, longitude: cfg.longitude, accuracy: 500, altitude: null, altitudeAccuracy: null, heading: null, speed: null }, timestamp: Date.now() });
      const success = (callback) => { if (typeof callback === "function") setTimeout(() => callback(position()), 0); };
      const location = {
        getCurrentPosition: (ok) => success(ok),
        watchPosition: (ok) => { const id = nextWatchId++; watchers.set(id, ok); success(ok); return id; },
        clearWatch: (id) => watchers.delete(id),
      };
      try { Object.defineProperty(nav, "geolocation", { configurable: true, value: location }); } catch (_) {}
    }
  })();`;

  const inject = () => {
    try {
      const pageWindow = window.wrappedJSObject || window;
      const document = pageWindow.document;
      const root = document.documentElement || document.head;
      if (!root) return false;
      const script = document.createElement("script");
      script.textContent = wrapped;
      root.appendChild(script);
      script.remove();
      return true;
    } catch (_) {
      return true;
    }
  };
  if (!inject()) {
    const observer = new MutationObserver(() => { if (inject()) observer.disconnect(); });
    observer.observe(document, { childList: true, subtree: true });
  }
}).catch(() => {});
