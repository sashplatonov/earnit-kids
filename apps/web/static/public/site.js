
import { DEFAULT_LOCALE, getMessage, normalizeLocale, resolveDocumentLocale } from "./i18n.js";
import { publicLanguageHref } from "./urls.js";

export const GOOGLE_WORKSPACE_FALLBACK = "/";
export const PUBLIC_SITE_STATIC_LOCALE = true;
const GOOGLE_WORKSPACE_START = "/api/login-google/start?continue=%2Fapp";

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
    const response = await fetchImpl(`/api/login-google/url?redirect_to=${encodeURIComponent(config.redirectTo || "/app")}`, {
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
  const locale = resolveDocumentLocale(documentRef);
  const pathname = windowRef.location.pathname;
  const savedLocale = String(documentRef.cookie || "").split(";").map((part) => part.trim().split("=")).find(([name]) => name === "locale")?.[1];
  const browserLocale = normalizeLocale(savedLocale) || normalizeLocale(windowRef.navigator?.languages?.[0]) || normalizeLocale(windowRef.navigator?.language);
  if (locale === DEFAULT_LOCALE && browserLocale === "ru" && !pathname.startsWith("/ru/")) {
    const target = pathname === "/" ? "/ru/" : `/ru${pathname}`;
    windowRef.location.assign(`${target}${windowRef.location.search}${windowRef.location.hash}`);
    return;
  }
  documentRef.querySelectorAll("[data-language]").forEach((link) => {
    const href = publicLanguageHref(windowRef.location.pathname, link.dataset.language, windowRef.location.origin);
    if (href) link.href = `${href}?lang=${encodeURIComponent(link.dataset.language)}`;
    link.addEventListener("click", () => {
      document.cookie = `locale=${encodeURIComponent(link.dataset.language)}; Path=/; Max-Age=${60 * 60 * 24 * 365}; SameSite=Lax`;
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
        windowRef.location.assign(await requestBrowserWorkspaceUrl(fetchImpl, { redirectTo: "/app" }));
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

  // Close the "About the app" dropdown on outside click and panel link click.
  const popover = documentRef.querySelector(".menu-popover");
  if (popover) {
    documentRef.addEventListener("click", (event) => {
      if (!popover.open) return;
      if (event.target instanceof Node && popover.contains(event.target)) return;
      popover.open = false;
    });
    popover.addEventListener("click", (event) => {
      if (event.target instanceof Element && event.target.closest(".menu-popover__panel a")) {
        popover.open = false;
      }
    });
  }

}

if (typeof window !== "undefined" && typeof document !== "undefined") {
  enhancePublicSite(document, window, window.fetch.bind(window));
}
