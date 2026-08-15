import { browser } from '$app/environment';

// EXPLAIN: Single source of truth for building public site URLs. The public
// EXPLAIN: origin comes from `appConfig.publicOrigin` (server-side config); in
// EXPLAIN: browser-only contexts without config, `window.location.origin` is
// EXPLAIN: used as a fallback. No hardcoded locale prefix — callers pass the
// EXPLAIN: bare path (e.g. `/`, `/how`) and the resolver normalizes it.

function normalizePath(path?: string): string {
    if (!path || path === '/') {
        return '/';
    }

    const withLeadingSlash = path.startsWith('/') ? path : `/${path}`;
    return withLeadingSlash.replace(/\/+$/, '') || '/';
}

function trimTrailingSlashes(value: string): string {
    return value.replace(/\/+$/, '');
}

/**
 * Build a public site URL from the configured public origin.
 *
 * @param publicOrigin - from `appConfig.publicOrigin`; required for SSR.
 * @param path - bare path without locale prefix (e.g. `/`, `/how`).
 * @returns absolute URL string.
 */
export function getPublicSiteUrl(publicOrigin: string, path?: string): string {
    const origin = trimTrailingSlashes(publicOrigin);
    return `${origin}${normalizePath(path)}`;
}

/**
 * Browser-only resolver that falls back to `window.location.origin` when no
 * config is available. Must not be called during SSR.
 */
export function getPublicSiteUrlBrowser(path?: string): string {
    const origin = browser ? window.location.origin : '';
    return getPublicSiteUrl(origin, path);
}