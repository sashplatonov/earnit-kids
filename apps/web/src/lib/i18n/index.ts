import {
    DEFAULT_LOCALE,
    buildAlternatePaths,
    localizePath,
    resolveDomainsForPath,
    swapPathLocale,
    type Locale,
    type MessageDomain,
} from './config';
import { formatCoins, formatDate, formatDateTime, formatMoneyLike, formatNumber, formatShortDate } from './formatters';
import { appMessages as enAppMessages } from './messages/en/app';
import { adminMessages as enAdminMessages } from './messages/en/admin';
import { analyticsMessages as enAnalyticsMessages } from './messages/en/analytics';
import { authMessages as enAuthMessages } from './messages/en/auth';
import { commonMessages as enCommonMessages } from './messages/en/common';
import { errorMessages as enErrorMessages } from './messages/en/errors';
import { historyMessages as enHistoryMessages } from './messages/en/history';
import { publicMessages as enPublicMessages } from './messages/en/public';
import { superadminMessages as enSuperadminMessages } from './messages/en/superadmin';
import { tasksMessages as enTasksMessages } from './messages/en/tasks';
import { adminMessages as ruAdminMessages } from './messages/ru/admin';
import { appMessages as ruAppMessages } from './messages/ru/app';
import { analyticsMessages as ruAnalyticsMessages } from './messages/ru/analytics';
import { authMessages as ruAuthMessages } from './messages/ru/auth';
import { commonMessages as ruCommonMessages } from './messages/ru/common';
import { errorMessages as ruErrorMessages } from './messages/ru/errors';
import { historyMessages as ruHistoryMessages } from './messages/ru/history';
import { publicMessages as ruPublicMessages } from './messages/ru/public';
import { superadminMessages as ruSuperadminMessages } from './messages/ru/superadmin';
import { tasksMessages as ruTasksMessages } from './messages/ru/tasks';

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
    resolveLegacyAlias,
    resolvePublicRedirect,
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
    formatShortDate,
} from './formatters';

const ENGLISH_DOMAIN_CATALOG = {
    common: enCommonMessages,
    public: enPublicMessages,
    auth: enAuthMessages,
    app: enAppMessages,
    admin: enAdminMessages,
    analytics: enAnalyticsMessages,
    history: enHistoryMessages,
    tasks: enTasksMessages,
    errors: enErrorMessages,
    superadmin: enSuperadminMessages,
} as const;

const RUSSIAN_DOMAIN_CATALOG = {
    common: ruCommonMessages,
    public: ruPublicMessages,
    auth: ruAuthMessages,
    app: ruAppMessages,
    admin: ruAdminMessages,
    analytics: ruAnalyticsMessages,
    history: ruHistoryMessages,
    tasks: ruTasksMessages,
    errors: ruErrorMessages,
    superadmin: ruSuperadminMessages,
} as const;

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

export type EnglishCatalog = typeof ENGLISH_DOMAIN_CATALOG;
export type MessageKey = PathKeys<EnglishCatalog>;
export type LoadedMessages = Partial<Record<MessageDomain, MessageTree>>;

export type TranslationVariables = Record<string, string | number>;

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
        return value === undefined ? match : String(value);
    });
}

function translate(payload: I18nPayload, key: MessageKey, variables?: TranslationVariables): string {
    const localized = getNestedValue(payload.messages, key);
    const english = getNestedValue(ENGLISH_DOMAIN_CATALOG, key);

    if (typeof localized === 'string') {
        return interpolate(localized, variables);
    }

    if (typeof english === 'string') {
        if (payload.locale !== DEFAULT_LOCALE && import.meta.env.DEV) {
            void import('$lib/logging/clientLogger').then(({ logClientWarn }) => {
                logClientWarn('i18n.missing_translation', 'Missing locale translation', {
                    locale: payload.locale,
                    key,
                });
            });
        }
        return interpolate(english, variables);
    }

    if (import.meta.env.DEV) {
        throw new Error(`Missing English translation for ${key}`);
    }

    return key;
}

export function buildI18nPayload(locale: Locale, domains: MessageDomain[]): I18nPayload {
    const uniqueDomains = [...new Set(domains)];
    const messages: LoadedMessages = {};

    for (const domain of uniqueDomains) {
        const englishMessages = ENGLISH_DOMAIN_CATALOG[domain] as MessageTree;
        const overlayMessages = locale === DEFAULT_LOCALE
            ? englishMessages
            : (RUSSIAN_DOMAIN_CATALOG[domain] as MessageTree);

        messages[domain] = locale === DEFAULT_LOCALE
            ? englishMessages
            : deepMerge(englishMessages, overlayMessages);
    }

    return {
        locale,
        domains: uniqueDomains,
        messages,
    };
}

export function getI18nPayloadForPath(pathname: string, locale: Locale): I18nPayload {
    return buildI18nPayload(locale, resolveDomainsForPath(pathname));
}

export function createTranslationRuntime(payload: I18nPayload) {
    return {
        locale: payload.locale,
        domains: payload.domains,
        messages: payload.messages,
        t: (key: MessageKey, variables?: TranslationVariables) => translate(payload, key, variables),
        href: (pathname: string) => localizePath(pathname, payload.locale),
        swapLocale: (pathname: string, locale: Locale) => swapPathLocale(pathname, locale),
        alternates: (pathname: string) => buildAlternatePaths(pathname),
        formatDate: (value: Date | number | string, options?: Intl.DateTimeFormatOptions) => formatDate(payload.locale, value, options),
        formatShortDate: (value: Date | number | string) => formatShortDate(payload.locale, value),
        formatDateTime: (value: Date | number | string) => formatDateTime(payload.locale, value),
        formatNumber: (value: number, options?: Intl.NumberFormatOptions) => formatNumber(payload.locale, value, options),
        formatMoneyLike: (value: number) => formatMoneyLike(payload.locale, value),
        formatCoins: (value: number) => formatCoins(payload.locale, value),
    };
}

export function translateKey(payload: I18nPayload, key: MessageKey, variables?: TranslationVariables): string {
    return translate(payload, key, variables);
}

export const FALLBACK_I18N_PAYLOAD = buildI18nPayload(DEFAULT_LOCALE, ['common', 'errors']);
