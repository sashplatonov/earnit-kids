export const LOCALES: readonly ['en', 'ru'];
export const DEFAULT_LOCALE: 'en';
export function normalizeLocale(value: unknown): 'en' | 'ru' | null;
export function resolveDocumentLocale(documentRef: { documentElement?: { lang?: string } }): 'en' | 'ru';
export function getMessage(locale: 'en' | 'ru', key: string): string;
export function withLanguage(href: string, locale: 'en' | 'ru', currentOrigin?: string): string;
export type PublicSiteMessages = Record<string, unknown> & { demo: string };
export const messages: Record<'en' | 'ru', PublicSiteMessages>;
