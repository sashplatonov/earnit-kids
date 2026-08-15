import type { Handle, HandleServerError } from '@sveltejs/kit';
import { redirect } from '@sveltejs/kit';
import {
    DEFAULT_LOCALE,
    LOCALE_COOKIE_NAME,
    localizePath,
    normalizeLocale,
    resolveLegacyAlias,
    resolvePublicRedirect,
    resolveLocaleFromAcceptLanguage,
    shouldCanonicalizePath,
    splitLocaleFromPath,
} from '$lib/i18n';
import { loadAppConfig } from '$lib/server/config';
import { resolveSessionSnapshot } from '$lib/server/session';

const SECURITY_HEADERS = {
    'cross-origin-resource-policy': 'same-site',
    'referrer-policy': 'no-referrer',
    'x-frame-options': 'DENY',
    'x-content-type-options': 'nosniff',
    'x-xss-protection': '1; mode=block',
    'strict-transport-security': 'max-age=31536000; includeSubDomains',
} as const;

export const handle: Handle = async ({ event, resolve }) => {
    event.locals.appConfig = loadAppConfig();
    const { locale: localeFromPath, pathname: internalPath } = splitLocaleFromPath(event.url.pathname);
    const cookieLocale = normalizeLocale(event.cookies.get(LOCALE_COOKIE_NAME));
    const headerLocale = resolveLocaleFromAcceptLanguage(event.request.headers.get('accept-language'));
    // EXPLAIN: The Telegram Mini App and the public marketing site are
    // EXPLAIN: Russian-only product surfaces served at bare URLs (no locale
    // EXPLAIN: prefix) so they can be shared as stable canonical links.
    const isTelegramMiniApp = internalPath === '/telegram' || internalPath.startsWith('/telegram/');
    const isPublicRoute = internalPath === '/'
        || internalPath === '/how'
        || internalPath === '/tasks'
        || internalPath === '/rewards'
        || internalPath === '/parents'
        || internalPath === '/faq';
    const isRussianOnlySurface = isTelegramMiniApp || isPublicRoute;
    const resolvedLocale = isRussianOnlySurface ? 'ru' : localeFromPath ?? cookieLocale ?? headerLocale ?? DEFAULT_LOCALE;

    event.locals.locale = resolvedLocale;

    // EXPLAIN: Public and Telegram routes live at bare URLs; do not redirect
    // EXPLAIN: `/` to the locale-prefixed `/en/` for those surfaces.
    if (event.url.pathname === '/' && !isPublicRoute) {
        throw redirect(302, `${localizePath('/', DEFAULT_LOCALE)}${event.url.search}`);
    }

    // EXPLAIN: The Mini App is Russian-only; serve it under the canonical /ru
    // EXPLAIN: prefix even when a request carries another locale in the path.
    if (isTelegramMiniApp && localeFromPath && localeFromPath !== 'ru') {
        throw redirect(302, `${localizePath(internalPath, 'ru')}${event.url.search}`);
    }

    // EXPLAIN: Permanent (308) redirects for legacy public pages replaced by the
    // EXPLAIN: new public site. Targets are bare canonical public URLs. This
    // EXPLAIN: must run before the generic legacy alias logic so marketing pages
    // EXPLAIN: redirect to the new bare public paths instead of locale-prefixed
    // EXPLAIN: legacy routes.
    const publicRedirectTarget = resolvePublicRedirect(internalPath);
    if (publicRedirectTarget) {
        throw redirect(308, `${publicRedirectTarget}${event.url.search}`);
    }

    const legacyAliasTarget = resolveLegacyAlias(internalPath);
    if (legacyAliasTarget) {
        throw redirect(302, `${localizePath(legacyAliasTarget, resolvedLocale)}${event.url.search}`);
    }

    if (shouldCanonicalizePath(event.url.pathname)) {
        throw redirect(302, `${localizePath(event.url.pathname, resolvedLocale)}${event.url.search}`);
    }

    event.locals.session = await resolveSessionSnapshot(event);

    if (localeFromPath && cookieLocale !== localeFromPath) {
        event.cookies.set(LOCALE_COOKIE_NAME, localeFromPath, {
            path: '/',
            sameSite: 'lax',
            maxAge: 60 * 60 * 24 * 365,
        });
    }

    const response = await resolve(event, {
        transformPageChunk: ({ html }) => html.replace('<html lang="ru">', `<html lang="${event.locals.locale}">`),
    });

    Object.entries(SECURITY_HEADERS).forEach(([key, value]) => {
        if (!response.headers.has(key)) {
            response.headers.set(key, value);
        }
    });

    return response;
};

export const handleError: HandleServerError = ({ error, event, message, status }) => {
    console.error('SvelteKit server error', {
        method: event.request.method,
        url: event.url.toString(),
        path: event.url.pathname,
        search: event.url.search,
        routeId: event.route.id ?? null,
        status,
        message,
        referer: event.request.headers.get('referer'),
        userAgent: event.request.headers.get('user-agent'),
        traceId: event.request.headers.get('x-trace-id'),
        error: error instanceof Error
            ? {
                name: error.name,
                message: error.message,
                stack: error.stack,
            }
            : String(error),
    });

    return { message };
};
