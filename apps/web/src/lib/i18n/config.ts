export const LOCALES = ['en', 'ru'] as const;
export const DEFAULT_LOCALE = 'en';
export const LOCALE_COOKIE_NAME = 'locale';

export type Locale = (typeof LOCALES)[number];
export type MessageDomain = 'common' | 'public' | 'auth' | 'app' | 'tasks' | 'admin' | 'errors';

const LOCALE_SET = new Set<string>(LOCALES);

const BYPASS_PREFIXES = [
    '/api',
    '/healthz',
    '/login-child',
    '/css',
    '/img',
    '/fonts',
    '/manifest.json',
    '/robots.txt',
    '/sitemap.xml',
    '/sw.js',
    '/.well-known',
    '/public',
    '/how',
    '/tasks',
    '/parents',
    '/faq',
] as const;

const LEGACY_ALIAS_MAP: Record<string, string> = {
    '/about.html': '/about',
    '/faq.html': '/faq',
    '/features': '/features/tasks',
    '/index.html': '/',
    '/login.html': '/login',
    '/super-admin.html': '/super-admin',
};

function normalisePath(pathname: string): string {
    if (!pathname || pathname === '/') {
        return '/';
    }

    const withLeadingSlash = pathname.startsWith('/') ? pathname : `/${pathname}`;
    const trimmed = withLeadingSlash.replace(/\/+$/, '');
    return trimmed || '/';
}

export function isLocale(value: string | null | undefined): value is Locale {
    return value != null && LOCALE_SET.has(value);
}

export function normalizeLocale(value: string | null | undefined): Locale | null {
    if (!value) {
        return null;
    }

    const normalized = value.trim().toLowerCase();
    if (normalized.startsWith('ru')) {
        return 'ru';
    }

    if (normalized.startsWith('en')) {
        return 'en';
    }

    return null;
}

export function resolveLocaleFromAcceptLanguage(header: string | null | undefined): Locale | null {
    if (!header) {
        return null;
    }

    const candidates = header
        .split(',')
        .map((entry) => {
            const [tag, qualityPart] = entry.trim().split(';');
            const quality = qualityPart?.startsWith('q=') ? Number(qualityPart.slice(2)) : 1;
            return {
                locale: normalizeLocale(tag),
                quality: Number.isFinite(quality) ? quality : 0,
            };
        })
        .filter((entry): entry is { locale: Locale; quality: number } => entry.locale !== null)
        .sort((left, right) => right.quality - left.quality);

    return candidates[0]?.locale ?? null;
}

export function splitLocaleFromPath(pathname: string): { locale: Locale | null; pathname: string } {
    const normalized = normalisePath(pathname);
    const segments = normalized.split('/').filter(Boolean);
    const locale = segments[0];

    if (!isLocale(locale)) {
        return {
            locale: null,
            pathname: normalized,
        };
    }

    const rest = `/${segments.slice(1).join('/')}`;
    return {
        locale,
        pathname: rest === '/' || rest === '' ? '/' : normalisePath(rest),
    };
}

export function getLocaleFromPath(pathname: string): Locale | null {
    return splitLocaleFromPath(pathname).locale;
}

export function stripLocaleFromPath(pathname: string): string {
    return splitLocaleFromPath(pathname).pathname;
}

export function localizePath(pathname: string, locale: Locale): string {
    const normalized = stripLocaleFromPath(pathname);
    return normalized === '/' ? `/${locale}/` : `/${locale}${normalized}`;
}

export function swapPathLocale(pathname: string, locale: Locale): string {
    return localizePath(pathname, locale);
}

export function isBypassedLocalePath(pathname: string): boolean {
    const normalized = normalisePath(pathname);
    return BYPASS_PREFIXES.some((prefix) => normalized === prefix || normalized.startsWith(`${prefix}/`));
}

export function resolveLegacyAlias(pathname: string): string | null {
    return LEGACY_ALIAS_MAP[normalisePath(pathname)] ?? null;
}

export function shouldCanonicalizePath(pathname: string): boolean {
    const normalized = normalisePath(pathname);
    return normalized !== '/' && !isBypassedLocalePath(normalized) && getLocaleFromPath(normalized) === null;
}

export function resolveDomainsForPath(pathname: string): MessageDomain[] {
    const internalPath = stripLocaleFromPath(pathname);

    // EXPLAIN: The public marketing site is a static HTML site in /public/*
    // EXPLAIN: and is not served by SvelteKit. These bare URLs redirect there
    // EXPLAIN: via hooks.server.ts, so they never reach the i18n domain resolver.
    if (
        internalPath === '/'
        || internalPath === '/how'
        || internalPath === '/tasks'
        || internalPath === '/rewards'
        || internalPath === '/parents'
        || internalPath === '/faq'
    ) {
        return ['common', 'errors'];
    }

    if (internalPath === '/login') {
        return ['common', 'public', 'auth', 'errors'];
    }

    if (internalPath === '/telegram' || internalPath.startsWith('/telegram/')
        || internalPath === '/workspace' || internalPath.startsWith('/workspace/')) {
        return ['common', 'app', 'tasks', 'admin', 'errors'];
    }

    return ['common', 'errors'];
}

export function buildAlternatePaths(pathname: string): Record<Locale | 'x-default', string> {
    const internalPath = stripLocaleFromPath(pathname);

    return {
        en: localizePath(internalPath, 'en'),
        ru: localizePath(internalPath, 'ru'),
        'x-default': localizePath(internalPath, DEFAULT_LOCALE),
    };
}
