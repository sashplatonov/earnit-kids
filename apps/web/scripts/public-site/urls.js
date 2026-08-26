export const PUBLIC_LOCALES = ['en', 'ru'];
export const DEFAULT_PUBLIC_LOCALE = 'en';

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

export function normalizePublicRequest(input) {
    const url = input instanceof URL ? new URL(input) : new URL(input, 'https://example.test');
    const current = publicDocument(url.pathname);
    if (!current || (url.pathname === '/' && url.searchParams.has('tgWebAppStartParam'))) {
        return { url, redirect: null, document: current };
    }

    const requestedLocale = normalizeLocale(url.searchParams.get('lang'));
    if (!requestedLocale) return { url, redirect: null, document: current };

    const target = publicDocument(current.englishPath, requestedLocale);
    url.pathname = target.path;
    url.searchParams.delete('lang');
    return { url, redirect: url.toString(), document: target };
}

export function publicLanguageHref(pathname, locale, origin = 'https://example.test') {
    const path = canonicalPublicPath(pathname.startsWith('/ru/') ? pathname.slice(3) || '/' : pathname, locale);
    if (!path) return null;
    return new URL(path, origin).toString();
}

export { PUBLIC_PAGES };
