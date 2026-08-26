export const PUBLIC_LOCALES: readonly ['en', 'ru'];
export const DEFAULT_PUBLIC_LOCALE: 'en';
export const DEFAULT_PUBLIC_ORIGIN: 'http://localhost:4174';
export const PUBLIC_PAGES: readonly { key: 'index' | 'how' | 'tasks' | 'rewards' | 'parents' | 'faq'; englishPath: string; artifact: string }[];
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
export function resolvePublicOrigin(rawOrigin?: string, options?: { production?: boolean }): string;
export function normalizePublicRequest(input: string | URL, options?: { acceptLanguage?: string }): {
    url: URL;
    redirect: string | null;
    document: ReturnType<typeof publicDocument>;
    vary: boolean;
};
export function publicLanguageHref(pathname: string, locale: 'en' | 'ru', origin?: string): string | null;
