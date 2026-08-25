import type { Handle, HandleServerError } from '@sveltejs/kit';
import { redirect } from '@sveltejs/kit';
import {
    DEFAULT_LOCALE,
    LOCALE_COOKIE_NAME,
    localizePath,
    normalizeLocale,
    resolveLocaleFromAcceptLanguage,
    shouldCanonicalizePath,
    splitLocaleFromPath,
} from '$lib/i18n';
import { loadAppConfig } from '$lib/server/config';
import { resolveSessionSnapshot } from '$lib/server/session';
import { emitDiagnostic } from '$lib/server/diagnostics';

const SECURITY_HEADERS = {
    'cross-origin-resource-policy': 'same-site',
    'permissions-policy': 'accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), payment=(), usb=() ',
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

    // EXPLAIN: The Telegram Mini App is served at a bare URL (no locale prefix).
    // The public marketing site is served from static/public/.
    const isTelegramMiniApp = internalPath === '/telegram' || internalPath.startsWith('/telegram/');
    const resolvedLocale = localeFromPath ?? cookieLocale ?? headerLocale ?? DEFAULT_LOCALE;

    event.locals.locale = resolvedLocale;

    event.locals.session = await resolveSessionSnapshot(event);
    const familyLocale = event.locals.session.authenticated ? normalizeLocale(event.locals.session.locale) : null;
    event.locals.locale = familyLocale ?? resolvedLocale;

    if (!isTelegramMiniApp && shouldCanonicalizePath(event.url.pathname)) {
        throw redirect(302, `${localizePath(event.url.pathname, event.locals.locale)}${event.url.search}`);
    }

    if (!isTelegramMiniApp && familyLocale && localeFromPath && localeFromPath !== familyLocale) {
        throw redirect(302, `${localizePath(internalPath, familyLocale)}${event.url.search}`);
    }

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

    const shouldSendHsts = event.url.protocol === 'https:' || process.env.DEPLOYMENT_ENV === 'production';
    Object.entries(SECURITY_HEADERS).forEach(([key, value]) => {
        if (key === 'strict-transport-security' && !shouldSendHsts) return;
        if (!response.headers.has(key)) {
            response.headers.set(key, value);
        }
    });

    return response;
};

export const handleError: HandleServerError = ({ error, event, message, status }) => {
    // EXPLAIN: Unknown public URLs are expected on an internet-facing edge
    // (for example, automated probes for WordPress or Tomcat endpoints). They
    // are already rendered by +error.svelte, so logging their stack traces as
    // server errors creates false alerts without adding diagnostic value.
    if (status === 404) {
        return { message };
    }

    emitDiagnostic({
        severity: 'error',
        code: 'web.server_error',
        route: event.route.id ?? event.url.pathname,
        status,
        category: 'render',
        traceId: event.request.headers.get('x-trace-id') ?? 'missing',
        errorClass: error instanceof Error ? error.name : 'UnknownError',
    });

    return { message };
};
