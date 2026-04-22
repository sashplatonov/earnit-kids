import type { Locale } from './config';

const INTL_LOCALES: Record<Locale, string> = {
    en: 'en-US',
    ru: 'ru-RU',
};

const COIN_LABELS = {
    en: {
        one: 'coin',
        other: 'coins',
    },
    ru: {
        one: 'монета',
        few: 'монеты',
        many: 'монет',
        other: 'монеты',
    },
} as const;

function toDate(value: Date | number | string): Date {
    return value instanceof Date ? value : new Date(value);
}

function getIntlLocale(locale: Locale): string {
    return INTL_LOCALES[locale];
}

export function formatNumber(locale: Locale, value: number, options?: Intl.NumberFormatOptions): string {
    return new Intl.NumberFormat(getIntlLocale(locale), options).format(value);
}

export function formatDate(locale: Locale, value: Date | number | string, options?: Intl.DateTimeFormatOptions): string {
    return new Intl.DateTimeFormat(getIntlLocale(locale), options).format(toDate(value));
}

export function formatShortDate(locale: Locale, value: Date | number | string): string {
    return formatDate(locale, value, {
        day: '2-digit',
        month: locale === 'ru' ? 'short' : 'short',
        year: 'numeric',
    });
}

export function formatDateTime(locale: Locale, value: Date | number | string): string {
    return formatDate(locale, value, {
        dateStyle: 'medium',
        timeStyle: 'short',
    });
}

export function formatMoneyLike(locale: Locale, value: number): string {
    return formatNumber(locale, value, {
        maximumFractionDigits: 0,
    });
}

export function getPluralCategory(locale: Locale, value: number): Intl.LDMLPluralRule {
    return new Intl.PluralRules(getIntlLocale(locale)).select(Math.abs(value));
}

export function formatCoins(locale: Locale, value: number): string {
    const category = getPluralCategory(locale, value);
    const labels = locale === 'ru' ? COIN_LABELS.ru : COIN_LABELS.en;
    const label = labels[category as keyof typeof labels] ?? labels.other;
    return `${formatNumber(locale, value)} ${label}`;
}
