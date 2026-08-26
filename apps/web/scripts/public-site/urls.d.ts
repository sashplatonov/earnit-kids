export const PUBLIC_LOCALES: readonly ['en', 'ru'];
export const DEFAULT_PUBLIC_LOCALE: 'en';
export function publicDocument(pathname: string, locale?: 'en' | 'ru'): {
    key: string;
    englishPath: string;
    artifact: string;
    locale: 'en' | 'ru';
    path: string;
    artifactPath: string;
} | null;
export function publicDocumentPath(url: string | URL, locale?: 'en' | 'ru'): string | null;
export function canonicalPublicPath(pathname: string, locale: 'en' | 'ru'): string | null;
export function normalizePublicRequest(input: string | URL): { url: URL; redirect: string | null; document: ReturnType<typeof publicDocument> };
export function publicLanguageHref(pathname: string, locale: 'en' | 'ru', origin?: string): string | null;
