import {
    DEFAULT_LOCALE,
    buildAlternatePaths,
    localizePath,
    resolveDomainsForPath,
    swapPathLocale,
    type Locale,
    type MessageDomain,
} from './config';
import { formatCoins, formatDate, formatDateTime, formatMoneyLike, formatNumber, formatPercentage, formatPlural, formatRelativeTime, formatShortDate } from './formatters';
import { adminMessages as enAdminMessages } from './messages/en/admin';
import { appMessages as enAppMessages } from './messages/en/app';
import { authMessages as enAuthMessages } from './messages/en/auth';
import { commonMessages as enCommonMessages } from './messages/en/common';
import { tasksMessages as enTasksMessages } from './messages/en/tasks';
import { errorMessages as enErrorMessages } from './messages/en/errors';

export { DEFAULT_LOCALE, LOCALES } from './config';
export type { Locale, MessageDomain } from './config';
export {
    buildAlternatePaths,
    getLocaleFromPath,
    isBypassedLocalePath,
    isLocale,
    localizePath,
    LOCALE_COOKIE_NAME,
    normalizeLocale,
    resolveLocaleFromAcceptLanguage,
    resolveDomainsForPath,
    shouldCanonicalizePath,
    splitLocaleFromPath,
    stripLocaleFromPath,
    swapPathLocale,
} from './config';
export {
    formatCoins,
    formatDate,
    formatDateTime,
    getPluralCategory,
    formatMoneyLike,
    formatNumber,
    formatPercentage,
    formatPlural,
    formatRelativeTime,
    formatShortDate,
} from './formatters';

type Primitive = string | number | boolean | null | undefined;
type MessageTree = {
    [key: string]: MessageTree | Primitive | readonly unknown[];
};

type PathKeys<T> = {
    [K in keyof T & string]: T[K] extends string
        ? K
        : T[K] extends Primitive | readonly unknown[]
            ? never
            : `${K}.${PathKeys<T[K]>}`;
}[keyof T & string];

export type EnglishCatalog = {
    common: typeof enCommonMessages;
    auth: typeof enAuthMessages;
    app: typeof enAppMessages;
    admin: typeof enAdminMessages;
    tasks: typeof enTasksMessages;
    errors: typeof enErrorMessages;
};
export type MessageKey = PathKeys<EnglishCatalog>;
export type LoadedMessages = Partial<Record<MessageDomain, MessageTree>>;

type PlaceholderNames<T extends string> = T extends `${string}{${infer Name}}${infer Rest}`
    ? Name | PlaceholderNames<Rest>
    : never;

type MessageAt<T, Path extends string> = Path extends `${infer Head}.${infer Rest}`
    ? Head extends keyof T ? MessageAt<T[Head], Rest> : never
    : Path extends keyof T ? T[Path] : never;

export type TranslationVariables = Record<string, string | number>;
export type TranslationVariablesForKey<K extends MessageKey> =
    [PlaceholderNames<KMessage<K>>] extends [never]
        ? Record<never, never>
        : { [Name in PlaceholderNames<KMessage<K>>]: string | number };

type KMessage<K extends MessageKey> = MessageAt<EnglishCatalog, K> & string;
export type TranslationFunction = {
    <K extends MessageKey>(key: K, variables?: TranslationVariablesForKey<K>): string;
    (key: MessageKey, variables?: TranslationVariables): string;
};

export type I18nPayload = {
    locale: Locale;
    domains: MessageDomain[];
    messages: LoadedMessages;
};

export type TranslationRuntime = ReturnType<typeof createTranslationRuntime>;

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function deepMerge(base: MessageTree, overlay: MessageTree | undefined): MessageTree {
    if (overlay === undefined) {
        return base;
    }

    if (!isRecord(base) || !isRecord(overlay)) {
        return overlay;
    }

    const result: Record<string, unknown> = { ...base };

    for (const [key, value] of Object.entries(overlay)) {
        const current = result[key];
        result[key] = isRecord(current) && isRecord(value)
            ? deepMerge(current as MessageTree, value as MessageTree)
            : value;
    }

    return result as MessageTree;
}

function getNestedValue(source: unknown, key: string): unknown {
    return key.split('.').reduce<unknown>((current, segment) => {
        if (!isRecord(current)) {
            return undefined;
        }

        return current[segment];
    }, source);
}

function interpolate(template: string, variables?: TranslationVariables): string {
    if (!variables) {
        return template;
    }

    return template.replace(/\{([\w-]+)\}/g, (match, key: string) => {
        const value = variables[key];
        if (value === undefined) {
            reportMissingVariable(key, template);
            return '';
        }
        return String(value);
    });
}

function reportMissingVariable(key: string, template: string): void {
    if (import.meta.env.DEV || import.meta.env.MODE === 'test') {
        console.warn(`[i18n] Missing interpolation variable "${key}" in "${template}"`);
    }
}

function translate(payload: I18nPayload, key: MessageKey, variables?: TranslationVariables): string {
    const localized = getNestedValue(payload.messages, key);
    if (typeof localized === 'string') {
        return interpolate(localized, variables);
    }

    if (import.meta.env.DEV || import.meta.env.MODE === 'test') {
        throw new Error(`Missing English translation for ${key}`);
    }

    return '';
}

const ENGLISH_DOMAIN_CATALOG = {
    common: enCommonMessages,
    auth: enAuthMessages,
    app: enAppMessages,
    tasks: enTasksMessages,
    admin: enAdminMessages,
    errors: enErrorMessages,
} as const satisfies Record<MessageDomain, MessageTree>;

type CatalogModule = { default?: unknown } & Record<string, unknown>;
type CatalogCache = Map<string, Promise<MessageTree>>;
const catalogCache: CatalogCache = new Map();

const RUSSIAN_DOMAIN_LOADERS: Record<MessageDomain, () => Promise<CatalogModule>> = {
    common: () => import('./messages/ru/common'),
    auth: () => import('./messages/ru/auth'),
    app: () => import('./messages/ru/app'),
    tasks: () => import('./messages/ru/tasks'),
    admin: () => import('./messages/ru/admin'),
    errors: () => import('./messages/ru/errors'),
};

async function importDomain(locale: Locale, domain: MessageDomain): Promise<MessageTree> {
    if (locale !== 'ru') {
        return ENGLISH_DOMAIN_CATALOG[domain];
    }

    const cacheKey = `${locale}:${domain}`;
    const cached = catalogCache.get(cacheKey);
    if (cached) return cached;

    const loading = RUSSIAN_DOMAIN_LOADERS[domain]().then((module: CatalogModule) => {
        const value = Object.values(module).find((candidate) => isRecord(candidate));
        if (!value) throw new Error(`Invalid ${locale}.${domain} translation catalog`);
        return value as MessageTree;
    });
    catalogCache.set(cacheKey, loading);
    return loading;
}

async function loadEnglishDomain(domain: MessageDomain): Promise<MessageTree> {
    return ENGLISH_DOMAIN_CATALOG[domain];
}

export async function buildI18nPayload(locale: Locale, domains: MessageDomain[]): Promise<I18nPayload> {
    const uniqueDomains = [...new Set(domains)];
    const messages: LoadedMessages = {};

    await Promise.all(uniqueDomains.map(async (domain) => {
        const englishMessages = await loadEnglishDomain(domain);
        const overlayMessages = locale === DEFAULT_LOCALE
            ? englishMessages
            : await importDomain(locale, domain);
        messages[domain] = locale === DEFAULT_LOCALE
            ? englishMessages
            : deepMerge(englishMessages, overlayMessages);
    }));

    return {
        locale,
        domains: uniqueDomains,
        messages,
    };
}

export async function getI18nPayloadForPath(pathname: string, locale: Locale): Promise<I18nPayload> {
    return buildI18nPayload(locale, resolveDomainsForPath(pathname));
}

export function createTranslationRuntime(payload: I18nPayload) {
    return {
        locale: payload.locale,
        domains: payload.domains,
        messages: payload.messages,
        t: ((key: MessageKey, variables?: TranslationVariables) => translate(payload, key, variables)) as TranslationFunction,
        href: (pathname: string) => localizePath(pathname, payload.locale),
        swapLocale: (pathname: string, locale: Locale) => swapPathLocale(pathname, locale),
        alternates: (pathname: string) => buildAlternatePaths(pathname),
        formatDate: (value: Date | number | string, options?: Intl.DateTimeFormatOptions) => formatDate(payload.locale, value, options),
        formatShortDate: (value: Date | number | string) => formatShortDate(payload.locale, value),
        formatDateTime: (value: Date | number | string) => formatDateTime(payload.locale, value),
        formatNumber: (value: number, options?: Intl.NumberFormatOptions) => formatNumber(payload.locale, value, options),
        formatMoneyLike: (value: number) => formatMoneyLike(payload.locale, value),
        formatCoins: (value: number) => formatCoins(payload.locale, value),
        formatPercentage: (value: number, maximumFractionDigits?: number) => formatPercentage(payload.locale, value, maximumFractionDigits),
        formatRelativeTime: (value: number, unit: Intl.RelativeTimeFormatUnit) => formatRelativeTime(payload.locale, value, unit),
        formatPlural: <T>(value: number, forms: Partial<Record<Intl.LDMLPluralRule, T>> & { other: T }) => formatPlural(payload.locale, value, forms),
    };
}

export function translateKey<K extends MessageKey>(payload: I18nPayload, key: K, variables?: TranslationVariablesForKey<K>): string {
    return translate(payload, key, variables);
}

export const FALLBACK_I18N_PAYLOAD: I18nPayload = {
    locale: DEFAULT_LOCALE,
    domains: ['common', 'errors'],
    messages: ENGLISH_DOMAIN_CATALOG,
};

function leafEntries(value: unknown, prefix = ''): Array<[string, string]> {
    if (typeof value === 'string') return [[prefix, value]];
    if (!isRecord(value)) return [];
    return Object.entries(value).flatMap(([key, child]) =>
        leafEntries(child, prefix ? `${prefix}.${key}` : key));
}

function catalogIssues(base: MessageTree, localized: MessageTree, locale: Locale, domain: MessageDomain): string[] {
    const baseEntries = new Map(leafEntries(base));
    const localizedEntries = new Map(leafEntries(localized));
    const issues: string[] = [];
    for (const [key, template] of baseEntries) {
        const localizedTemplate = localizedEntries.get(key);
        if (localizedTemplate === undefined) issues.push(`${locale}.${domain}.${key}: missing key`);
        else if (new Set(template.match(/\{[\w-]+\}/g) ?? []).size !== new Set(localizedTemplate.match(/\{[\w-]+\}/g) ?? []).size
            || (template.match(/\{[\w-]+\}/g) ?? []).sort().join() !== (localizedTemplate.match(/\{[\w-]+\}/g) ?? []).sort().join()) {
            issues.push(`${locale}.${domain}.${key}: placeholder mismatch`);
        }
    }
    for (const key of localizedEntries.keys()) {
        if (!baseEntries.has(key)) issues.push(`${locale}.${domain}.${key}: unexpected key`);
    }
    return issues;
}

export async function validateCatalogs(): Promise<string[]> {
    const issues: string[] = [];
    for (const domain of ['common', 'auth', 'app', 'admin', 'tasks', 'errors'] as MessageDomain[]) {
        const english = await loadEnglishDomain(domain);
        const russian = await importDomain('ru', domain);
        issues.push(...catalogIssues(english, russian, 'ru', domain));
    }
    return issues;
}
