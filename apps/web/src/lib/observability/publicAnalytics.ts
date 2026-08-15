import { logBrowser } from '$lib/observability/newrelic';

// EXPLAIN: Minimal public-site analytics contract. Three events only:
//   - public_page_view  -> which public pages are actually visited
//   - public_cta_click  -> where users open the Mini App from (by placement)
//   - public_share      -> whether sharing is used
// Reuses the existing New Relic browser logging; when the browser agent is
// disabled (VITE_NEW_RELIC_BROWSER_ENABLED=false) no events are sent.
// Never sends child names, user task/reward data, Telegram identifiers, or PII.

export type PublicAnalyticsPlacement =
    | 'hero'
    | 'header'
    | 'mobile_menu'
    | 'footer'
    | 'share_control';

export type PublicAnalyticsEvent =
    | 'public_page_view'
    | 'public_cta_click'
    | 'public_share';

function normalizePage(page: string): string {
    return page === '' ? '/' : page;
}

export function trackPublicPageView(page: string): void {
    logBrowser('info', 'public_page_view', 'Public page viewed', {
        page: normalizePage(page),
    });
}

export function trackPublicCtaClick(placement: PublicAnalyticsPlacement, page: string): void {
    logBrowser('info', 'public_cta_click', 'Public CTA clicked', {
        placement,
        page: normalizePage(page),
    });
}

export function trackPublicShare(page: string): void {
    logBrowser('info', 'public_share', 'Public site shared', {
        page: normalizePage(page),
    });
}
