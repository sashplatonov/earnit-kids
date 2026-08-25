
export const GOOGLE_WORKSPACE_FALLBACK = "/public/index.html";
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
  const cfg = windowRef.EARNIT_CONFIG || {};
  const url = String(cfg.telegramMiniAppUrl || "");
  const configured = url && url !== "#" && !/REPLACE_WITH_/i.test(url);

  documentRef.querySelectorAll("[data-miniapp-link]").forEach((link) => {
    if (configured) {
      link.href = url;
    } else {
      link.href = "#";
      link.setAttribute("aria-disabled", "true");
      link.title = "Ссылка на Telegram Mini App пока не настроена";
      link.addEventListener("click", (event) => {
        event.preventDefault();
        alert("Укажите реальную ссылку Telegram Mini App в config.js");
      });
    }
  });

  const status = documentRef.createElement("p");
  status.className = "public-access-status sr-only";
  status.setAttribute("aria-live", "polite");
  status.setAttribute("role", "status");
  documentRef.body.append(status);

  if (new URL(windowRef.location.href).searchParams.get("error")) {
    status.textContent = documentRef.documentElement.lang === "en"
      ? "Google sign-in is temporarily unavailable. Use the browser sign-in link to try again."
      : "Вход через Google временно недоступен. Используйте ссылку для входа в браузере и попробуйте ещё раз.";
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
        status.textContent = documentRef.documentElement.lang === "en"
          ? "Google sign-in is temporarily unavailable. Use the browser sign-in link to try again."
          : "Вход через Google временно недоступен. Используйте ссылку для входа в браузере и попробуйте ещё раз.";
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

  documentRef.querySelectorAll("[data-carousel]").forEach((carousel) => {
    const track = carousel.querySelector(".carousel-track");
    const slides = Array.from(carousel.querySelectorAll(".carousel-slide"));
    const dots = Array.from(carousel.querySelectorAll(".carousel-dot"));
    const prev = carousel.querySelector(".carousel-prev");
    const next = carousel.querySelector(".carousel-next");
    const status = carousel.querySelector(".carousel-status");
    const labels = slides.map((slide) => slide.querySelector("figcaption b")?.textContent || "Экран");
    let index = 0;
    let startX = null;

    const render = () => {
      track.style.transform = `translateX(-${index * 100}%)`;
      dots.forEach((dot, i) => {
        dot.classList.toggle("is-active", i === index);
        if (i === index) dot.setAttribute("aria-current", "true");
        else dot.removeAttribute("aria-current");
      });
      if (status) status.textContent = `Экран ${index + 1} из ${slides.length}: ${labels[index]}`;
    };

    const move = (delta) => {
      index = (index + delta + slides.length) % slides.length;
      render();
    };

    prev?.addEventListener("click", () => move(-1));
    next?.addEventListener("click", () => move(1));
    dots.forEach((dot, i) => dot.addEventListener("click", () => { index = i; render(); }));

    carousel.tabIndex = 0;
    carousel.addEventListener("keydown", (event) => {
      if (event.key === "ArrowLeft") { event.preventDefault(); move(-1); }
      if (event.key === "ArrowRight") { event.preventDefault(); move(1); }
      if (event.key === "Home") { event.preventDefault(); index = 0; render(); }
      if (event.key === "End") { event.preventDefault(); index = slides.length - 1; render(); }
    });

    carousel.addEventListener("pointerdown", (event) => { startX = event.clientX; });
    carousel.addEventListener("pointerup", (event) => {
      if (startX === null) return;
      const delta = event.clientX - startX;
      if (Math.abs(delta) > 45) move(delta < 0 ? 1 : -1);
      startX = null;
    });
    carousel.addEventListener("pointercancel", () => { startX = null; });

    render();
  });
}

if (typeof window !== "undefined" && typeof document !== "undefined") {
  enhancePublicSite(document, window, window.fetch.bind(window));
}
