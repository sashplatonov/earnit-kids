/** @file Mobile shell layout metrics helpers */
const MOBILE_LAYOUT_QUERY = '(max-width: 900px)';

function syncAppShellMetrics() {
    const root = document.documentElement;
    const header = document.querySelector('.header');
    const nav = document.querySelector('.nav');
    const headerHeight = header ? Math.ceil(header.getBoundingClientRect().height) : 0;
    const isMobile = window.matchMedia(MOBILE_LAYOUT_QUERY).matches;
    const navPosition = nav ? getComputedStyle(nav).position : '';
    const navHeight = isMobile && nav && navPosition === 'fixed'
        ? Math.ceil(nav.getBoundingClientRect().height)
        : 0;

    if (headerHeight > 0) {
        root.style.setProperty('--header-height', `${headerHeight}px`);
    }
    root.style.setProperty('--bottom-nav-height', `${navHeight}px`);
    root.style.setProperty('--fab-safe-offset', `${Math.max(76, navHeight + 16)}px`);
}

function applyMobileViewportBudgets() {
    const root = document.documentElement;
    if (!window.matchMedia(MOBILE_LAYOUT_QUERY).matches) {
        root.style.removeProperty('--mobile-header-max-height');
        root.style.removeProperty('--mobile-nav-height');
        root.style.removeProperty('--mobile-main-bottom-offset');
        syncAppShellMetrics();
        return;
    }

    const viewportHeight = Math.max(window.innerHeight || 0, 0);
    if (!viewportHeight) return;

    const headerBudget = Math.round(viewportHeight * 0.12);
    const navBudget = Math.round(viewportHeight * 0.10);
    const navHeight = Math.max(56, Math.min(72, navBudget));

    root.style.setProperty('--mobile-header-max-height', `${Math.max(72, headerBudget)}px`);
    root.style.setProperty('--mobile-nav-height', `${navHeight}px`);
    root.style.setProperty('--mobile-main-bottom-offset', `${navHeight + 60}px`);
    syncAppShellMetrics();
}

export function setupMobileViewportBudgets() {
    applyMobileViewportBudgets();
    window.addEventListener('resize', applyMobileViewportBudgets, { passive: true });
    window.addEventListener('orientationchange', applyMobileViewportBudgets, { passive: true });
    window.visualViewport?.addEventListener('resize', applyMobileViewportBudgets, { passive: true });
}

export function syncMobileShellMetrics() {
    syncAppShellMetrics();
}
