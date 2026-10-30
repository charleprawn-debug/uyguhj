browser.runtime.sendNativeMessage("kun.browser", { action: "profile-environment" }).then((config) => {
  if (!config || config.enabled !== true) return;
  const pageConfig = JSON.stringify({
    timeZone: typeof config.timeZone === "string" ? config.timeZone : "",
    locationMode: typeof config.locationMode === "string" ? config.locationMode : "auto",
    latitude: Number(config.latitude),
    longitude: Number(config.longitude),
  });
  const wrapped = `(() => {
    const cfg = ${pageConfig};
    const nav = window.navigator;
    if (cfg.timeZone) {
      const NativeDateTimeFormat = Intl.DateTimeFormat;
      const originalDateMethods = {};
      function ProfileDateTimeFormat(locales, options) {
        const adjusted = Object.assign({}, options || {});
        if (!Object.prototype.hasOwnProperty.call(adjusted, "timeZone")) adjusted.timeZone = cfg.timeZone;
        return new NativeDateTimeFormat(locales, adjusted);
      }
      ProfileDateTimeFormat.prototype = NativeDateTimeFormat.prototype;
      Object.setPrototypeOf(ProfileDateTimeFormat, NativeDateTimeFormat);
      ProfileDateTimeFormat.supportedLocalesOf = NativeDateTimeFormat.supportedLocalesOf.bind(NativeDateTimeFormat);
      try { Intl.DateTimeFormat = ProfileDateTimeFormat; } catch (_) {}
      const formatter = new NativeDateTimeFormat("en-US-u-ca-gregory-nu-latn", {
        timeZone: cfg.timeZone, year: "numeric", month: "2-digit", day: "2-digit",
        hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23",
      });
      const numericParts = (date) => {
        if (!Number.isFinite(date.getTime())) return null;
        const values = {};
        for (const part of formatter.formatToParts(date)) if (part.type !== "literal") values[part.type] = Number(part.value);
        return values;
      };
      const getterNames = ["getFullYear", "getMonth", "getDate", "getHours", "getMinutes", "getSeconds"];
      for (const name of getterNames) {
        originalDateMethods[name] = Date.prototype[name];
        Date.prototype[name] = function () {
          const parts = numericParts(this);
          if (!parts) return NaN;
          return ({ getFullYear: () => parts.year, getMonth: () => parts.month - 1, getDate: () => parts.day,
            getHours: () => parts.hour, getMinutes: () => parts.minute, getSeconds: () => parts.second })[name]();
        };
      }
      originalDateMethods.getDay = Date.prototype.getDay;
      Date.prototype.getDay = function () {
        const parts = numericParts(this);
        return parts ? new Date(Date.UTC(parts.year, parts.month - 1, parts.day)).getUTCDay() : NaN;
      };
      originalDateMethods.getYear = Date.prototype.getYear;
      Date.prototype.getYear = function () { const year = this.getFullYear(); return Number.isNaN(year) ? NaN : year - 1900; };
      originalDateMethods.getTimezoneOffset = Date.prototype.getTimezoneOffset;
      Date.prototype.getTimezoneOffset = function () {
        const parts = numericParts(this);
        if (!parts) return NaN;
        const asUtc = Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute, parts.second, this.getUTCMilliseconds());
        return (this.getTime() - asUtc) / 60000;
      };
      originalDateMethods.toString = Date.prototype.toString;
      Date.prototype.toString = function () {
        if (!Number.isFinite(this.getTime())) return "Invalid Date";
        return new NativeDateTimeFormat("en-US", {
          timeZone: cfg.timeZone, weekday: "short", month: "short", day: "2-digit", year: "numeric",
          hour: "2-digit", minute: "2-digit", second: "2-digit", timeZoneName: "short",
        }).format(this);
      };
      for (const method of ["toLocaleString", "toLocaleDateString", "toLocaleTimeString"]) {
        originalDateMethods[method] = Date.prototype[method];
        Date.prototype[method] = function (locales, options) {
          const adjusted = Object.assign({}, options || {});
          if (!Object.prototype.hasOwnProperty.call(adjusted, "timeZone")) adjusted.timeZone = cfg.timeZone;
          return new NativeDateTimeFormat(locales, adjusted).format(this);
        };
      }
    }
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
