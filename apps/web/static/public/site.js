
import { applyLocale, getMessage, resolveLocale, withLanguage } from "./i18n.js";

export const GOOGLE_WORKSPACE_FALLBACK = "/";
const GOOGLE_WORKSPACE_START = "/api/login-google/start?continue=%2Fworkspace";

function isUsableAuthorizationUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === "https:" && url.hostname === "accounts.google.com";
  } catch {
    return false;
  }
}

export async function requestBrowserWorkspaceUrl(fetchImpl, config = {}) {
  let authConfig;

  try {
    const configResponse = await fetchImpl("/api/auth-config", {
      credentials: "same-origin",
      cache: "no-store",
    });
    if (!configResponse.ok) throw new Error("config unavailable");
    authConfig = await configResponse.json();
  } catch {
    throw new Error("unavailable");
  }

  if (authConfig?.googleEnabled !== true) throw new Error("unavailable");

  try {
    const response = await fetchImpl(`/api/login-google/url?redirect_to=${encodeURIComponent(config.redirectTo || "/workspace")}`, {
      credentials: "same-origin",
      cache: "no-store",
    });
    const body = await response.json().catch(() => ({}));
    if (response.ok && typeof body.url === "string" && isUsableAuthorizationUrl(body.url)) return body.url;
  } catch {
    // Keep the local login fallback available when OAuth startup is offline.
  }

  throw new Error("unavailable");
}

export function enhancePublicSite(documentRef, windowRef, fetchImpl) {
  const locale = resolveLocale(windowRef.location.search, windowRef.navigator);
  applyLocale(documentRef, windowRef, locale);
  documentRef.querySelectorAll("[data-language]").forEach((button) => {
    button.addEventListener("click", () => {
      windowRef.location.assign(withLanguage(windowRef.location.pathname, button.dataset.language, windowRef.location.origin));
    });
  });
  const cfg = windowRef.EARNIT_CONFIG || {};
  const url = String(cfg.telegramMiniAppUrl || "");
  const configured = url && url !== "#" && !/REPLACE_WITH_/i.test(url);

  documentRef.querySelectorAll("[data-miniapp-link]").forEach((link) => {
    if (configured) {
      link.href = url;
    } else {
      link.href = "#";
      link.setAttribute("aria-disabled", "true");
      link.title = getMessage(locale, "unavailableMiniApp");
      link.addEventListener("click", (event) => {
        event.preventDefault();
        alert(getMessage(locale, "configureMiniApp"));
      });
    }
  });

  const status = documentRef.createElement("p");
  status.className = "public-access-status sr-only";
  status.setAttribute("aria-live", "polite");
  status.setAttribute("role", "status");
  documentRef.body.append(status);

  if (new URL(windowRef.location.href).searchParams.get("error")) {
    status.textContent = getMessage(locale, "oauthError");
  }

  documentRef.querySelectorAll("[data-browser-workspace-link]").forEach((link) => {
    link.addEventListener("click", async (event) => {
      if (link.dataset.browserFallback === "true") return;

      event.preventDefault();
      status.textContent = "";
      link.setAttribute("aria-busy", "true");
      try {
        windowRef.location.assign(await requestBrowserWorkspaceUrl(fetchImpl, { redirectTo: "/workspace" }));
      } catch {
        link.href = GOOGLE_WORKSPACE_START;
        status.textContent = getMessage(locale, "oauthError");
      } finally {
        link.removeAttribute("aria-busy");
      }
    });
  });

  const tabs = documentRef.querySelector(".tabs");
  const activeTab = tabs && tabs.querySelector(".tab.active");
  if (tabs && activeTab && window.matchMedia("(max-width: 640px)").matches) {
    requestAnimationFrame(() => {
      const target = activeTab.offsetLeft - (tabs.clientWidth - activeTab.offsetWidth) / 2;
      tabs.scrollTo({ left: Math.max(0, target), behavior: "auto" });
    });
  }

}

if (typeof window !== "undefined" && typeof document !== "undefined") {
  enhancePublicSite(document, window, window.fetch.bind(window));
}
