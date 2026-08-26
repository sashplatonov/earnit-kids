export const LOCALES: readonly ['en', 'ru'];
export const DEFAULT_LOCALE: 'en';
export function normalizeLocale(value: unknown): 'en' | 'ru' | null;
export function detectLocale(navigatorRef?: { languages?: string[]; language?: string }): 'en' | 'ru';
export function resolveLocale(search?: string, navigatorRef?: { languages?: string[]; language?: string }): 'en' | 'ru';
export function getMessage(locale: 'en' | 'ru', key: string): string;
export function withLanguage(href: string, locale: 'en' | 'ru', currentOrigin?: string): string;
export const messages: Record<'en' | 'ru', Record<string, unknown>>;
