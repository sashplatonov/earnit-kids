export const PUBLIC_LOCALES = ['en', 'ru'];
export const DEFAULT_PUBLIC_LOCALE = 'en';
export const DEFAULT_PUBLIC_ORIGIN = 'http://localhost:4174';

const PUBLIC_PAGES = [
    { key: 'index', englishPath: '/', artifact: 'index.html' },
    { key: 'how', englishPath: '/how.html', artifact: 'how.html' },
    { key: 'tasks', englishPath: '/tasks.html', artifact: 'tasks.html' },
    { key: 'rewards', englishPath: '/rewards.html', artifact: 'rewards.html' },
    { key: 'parents', englishPath: '/parents.html', artifact: 'parents.html' },
    { key: 'faq', englishPath: '/faq.html', artifact: 'faq.html' },
];

function normalizeLocale(value) {
    if (typeof value !== 'string') return null;
    const locale = value.trim().toLowerCase();
    return PUBLIC_LOCALES.includes(locale) ? locale : null;
}

export function resolvePublicOrigin(rawOrigin, { production = false } = {}) {
    const value = typeof rawOrigin === 'string' && rawOrigin.trim() ? rawOrigin.trim() : null;
    if (!value) {
        if (production) throw new Error('APP_URL is required for a production public-site build');
        return DEFAULT_PUBLIC_ORIGIN;
    }

    try {
        const url = new URL(value);
        if (!['http:', 'https:'].includes(url.protocol) || !url.hostname) throw new Error('unsupported origin');
        return url.origin;
    } catch {
        throw new Error(`APP_URL must be a valid HTTP(S) URL: ${value}`);
    }
}

function preferredPublicLocale(acceptLanguage) {
    if (typeof acceptLanguage !== 'string') return DEFAULT_PUBLIC_LOCALE;

    const preferences = acceptLanguage.split(',')
        .map((part, index) => {
            const [range, ...parameters] = part.trim().toLowerCase().split(';');
            const qualityParameter = parameters.find((parameter) => parameter.trim().startsWith('q='));
            const quality = qualityParameter ? Number(qualityParameter.trim().slice(2)) : 1;
            return {
                range: range.trim(),
                quality: Number.isFinite(quality) ? Math.max(0, Math.min(1, quality)) : 0,
                index,
            };
        })
        .filter(({ range, quality }) => range && quality > 0)
        .sort((left, right) => right.quality - left.quality || left.index - right.index);

    const supportedPreference = preferences.find(({ range }) => {
        const language = range.split('-')[0];
        return PUBLIC_LOCALES.includes(language);
    });

    return supportedPreference?.range.split('-')[0] || DEFAULT_PUBLIC_LOCALE;
}

export function publicDocument(pathname, locale = DEFAULT_PUBLIC_LOCALE) {
    const normalizedLocale = normalizeLocale(locale);
    if (!normalizedLocale) return null;

    const page = PUBLIC_PAGES.find(({ englishPath }) => englishPath === pathname);
    if (page) {
        return {
            ...page,
            locale: normalizedLocale,
            path: normalizedLocale === DEFAULT_PUBLIC_LOCALE ? page.englishPath : `/ru${page.englishPath}`,
            artifactPath: `/public/${normalizedLocale === DEFAULT_PUBLIC_LOCALE ? '' : 'ru/'}${page.artifact}`,
        };
    }

    if (!pathname.startsWith('/ru/')) return null;
    const englishPath = pathname.slice('/ru'.length);
    const russianPage = PUBLIC_PAGES.find((candidate) => candidate.englishPath === englishPath);
    if (!russianPage) return null;

    return {
        ...russianPage,
        locale: 'ru',
        path: pathname,
        artifactPath: `/public/ru/${russianPage.artifact}`,
    };
}

export function publicDocumentPath(url, locale) {
    const pathname = typeof url === 'string' ? new URL(url, 'https://example.test').pathname : url.pathname;
    return publicDocument(pathname, locale)?.artifactPath || null;
}

export function canonicalPublicPath(pathname, locale) {
    return publicDocument(pathname, locale)?.path || null;
}

export function normalizePublicRequest(input, { acceptLanguage } = {}) {
    const url = input instanceof URL ? new URL(input) : new URL(input, 'https://example.test');
    const current = publicDocument(url.pathname);
    if (!current || (url.pathname === '/' && url.searchParams.has('tgWebAppStartParam'))) {
        return { url, redirect: null, document: current, vary: false };
    }

    const queryLocale = url.searchParams.get('lang');
    const requestedLocale = normalizeLocale(queryLocale);
    if (queryLocale !== null && !requestedLocale) {
        return { url, redirect: null, document: current, vary: false };
    }

    const negotiatedLocale = requestedLocale || (
        current.locale === DEFAULT_PUBLIC_LOCALE ? preferredPublicLocale(acceptLanguage) : null
    );
    if (!negotiatedLocale || (!requestedLocale && negotiatedLocale === current.locale)) {
        return { url, redirect: null, document: current, vary: !requestedLocale && current.locale === DEFAULT_PUBLIC_LOCALE && typeof acceptLanguage === 'string' };
    }

    const target = publicDocument(current.englishPath, negotiatedLocale);
    url.pathname = target.path;
    url.searchParams.delete('lang');
    return {
        url,
        redirect: url.toString(),
        document: target,
        vary: !requestedLocale,
    };
}

export function publicLanguageHref(pathname, locale, origin = 'https://example.test') {
    const path = canonicalPublicPath(pathname.startsWith('/ru/') ? pathname.slice(3) || '/' : pathname, locale);
    if (!path) return null;
    return new URL(path, resolvePublicOrigin(origin)).toString();
}

export { PUBLIC_PAGES };
