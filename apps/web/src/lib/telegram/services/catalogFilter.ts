/**
 * Pure helpers for the parent ready catalog: filtering, duplicate detection,
 * frequency formatting and group mapping. Kept free of Svelte/DOM so it can be
 * unit-tested in isolation.
 */
import type { CatalogTaskTemplate, Task } from '$lib/stores/app';
import type { CatalogRewardTemplate, ShopItem } from '$lib/telegram/stores/types';
import type { Locale } from '$lib/i18n';

export type CatalogKind = 'task' | 'reward';

export type AgeFilter = '6-8' | '9-11' | '12-14' | null;

export type DifficultyFilter = 'simple' | 'normal' | 'advanced' | null;

export type FrequencyFilter = 'daily' | 'weekly' | 'unlimited' | null;

export interface CatalogFilters {
    age: AgeFilter;
    difficulty: DifficultyFilter;
    frequency: FrequencyFilter;
    /** Reward-only semantic filters. */
    purchase: 'none' | 'purchase' | 'together' | null;
}

export const EMPTY_FILTERS: CatalogFilters = {
    age: null,
    difficulty: null,
    frequency: null,
    purchase: null,
};

const AGE_RANGE: Record<Exclude<AgeFilter, null>, [number, number]> = {
    '6-8': [6, 8],
    '9-11': [9, 11],
    '12-14': [12, 14],
};

/** Number of non-age active filters (for the "Фильтры · N" badge). */
export function nonAgeFilterCount(filters: CatalogFilters): number {
    let count = 0;
    if (filters.difficulty) count += 1;
    if (filters.frequency) count += 1;
    if (filters.purchase) count += 1;
    return count;
}

export function matchesAge(item: { minAge?: number | null; maxAge?: number | null }, age: AgeFilter): boolean {
    if (!age) return true;
    const [min, max] = AGE_RANGE[age];
    const itemMin = item.minAge ?? 6;
    const itemMax = item.maxAge ?? 14;
    // Item supports the age if its range overlaps the selected bucket.
    return itemMin <= max && itemMax >= min;
}

export function matchesDifficulty(item: { difficulty?: string | null }, difficulty: DifficultyFilter): boolean {
    if (!difficulty) return true;
    return (item.difficulty ?? 'normal') === difficulty;
}

export function matchesFrequency(item: { frequencyLimit?: number | null; frequencyPeriod?: string | null }, frequency: FrequencyFilter): boolean {
    if (!frequency) return true;
    const period = item.frequencyPeriod ?? 'week';
    const limit = item.frequencyLimit ?? 1;
    if (frequency === 'daily') return period === 'day';
    if (frequency === 'weekly') return period === 'week';
    // unlimited = no limit (limit 0 or null) OR a long period (month/year)
    if (frequency === 'unlimited') return limit == null || limit === 0 || period === 'month' || period === 'year';
    return true;
}

export function matchesPurchase(item: { groupKey?: string; tags?: string[] }, purchase: CatalogFilters['purchase']): boolean {
    if (!purchase) return true;
    const tags = item.tags ?? [];
    const groupKey = item.groupKey ?? '';
    if (purchase === 'none') return groupKey !== 'purchases' && !tags.includes('purchases');
    if (purchase === 'purchase') return groupKey === 'purchases' || tags.includes('purchases');
    // together time
    return groupKey === 'family' || groupKey === 'privileges' || tags.includes('family');
}

export function matchesSearch(item: { title?: string; comment?: string | null; groupName?: string; tags?: string[] }, query: string): boolean {
    const q = query.trim().toLowerCase();
    if (!q) return true;
    const title = (item.title ?? '').toLowerCase();
    const comment = (item.comment ?? '').toLowerCase();
    const group = (item.groupName ?? '').toLowerCase();
    const tags = (item.tags ?? []).join(' ').toLowerCase();
    return title.includes(q) || comment.includes(q) || group.includes(q) || tags.includes(q);
}

export function filterCatalog(
    items: readonly (CatalogTaskTemplate | CatalogRewardTemplate)[],
    filters: CatalogFilters,
    query: string
): Array<CatalogTaskTemplate | CatalogRewardTemplate> {
    return items.filter((item) =>
        matchesAge(item, filters.age)
        && matchesDifficulty(item, filters.difficulty)
        && matchesFrequency(item, filters.frequency)
        && matchesPurchase(item, filters.purchase)
        && matchesSearch(item, query)
    );
}

/** Stable catalog group list in canonical order (groupName order of appearance). */
export function catalogGroups<T extends { groupName?: string }>(items: readonly T[]): string[] {
    const seen: string[] = [];
    for (const item of items) {
        const name = item.groupName?.trim();
        if (name && !seen.includes(name)) seen.push(name);
    }
    return seen;
}

/** Create a deterministic local-only id. */
function generateLocalId(): string {
    const time = Date.now().toString(36);
    const random = Math.random().toString(36).slice(2, 8);
    return `${time}-${random}`;
}

/**
 * Duplicate detection: a family item is a copy of a catalog template when it
 * carries the same `sourceCatalogItemId`, or when its normalized title matches
 * the template title (after stripping the leading emoji).
 */
export function isAlreadyAdded(
    template: { id: string; title: string },
    familyItems: readonly (Task | ShopItem)[]
): boolean {
    const templateTitle = stripEmoji(template.title).toLowerCase();
    return familyItems.some((item) => {
        const sourceId = (item as { sourceCatalogItemId?: string | null }).sourceCatalogItemId;
        if (sourceId && String(sourceId) === String(template.id)) return true;
        const itemTitle = stripEmoji(String(item.name ?? '')).toLowerCase();
        return itemTitle === templateTitle;
    });
}

/** Strip a leading emoji (content exception) for comparison/display. */
export function stripEmoji(value: string): string {
    // eslint-disable-next-line no-misleading-character-class
    return value.replace(/^[\p{Extended_Pictographic}\u{200D}\u{FE0F}\s]+/u, '').trim();
}

/** Format a frequency pair into a short Russian label. */
export function formatFrequency(limit: number | null | undefined, period: string | null | undefined, locale: Locale = 'ru'): string {
    const resolvedLimit = limit ?? 1;
    const resolvedPeriod = period ?? 'week';
    const periodLabel: Record<Locale, Record<string, string>> = {
        en: { day: 'day', week: 'week', month: 'month', year: 'year' },
        ru: { day: 'день', week: 'неделю', month: 'месяц', year: 'год' },
    };
    const label = periodLabel[locale][resolvedPeriod] ?? periodLabel[locale].week;
    if (resolvedLimit == null || resolvedLimit === 0) return locale === 'ru' ? 'Без лимита' : 'Unlimited';
    if (locale === 'en') return `${resolvedLimit} time${resolvedLimit === 1 ? '' : 's'} per ${label}`;
    if (resolvedLimit === 1) return `1 раз в ${label}`;
    if (resolvedLimit === 2) return `2 раза в ${label}`;
    return `${resolvedLimit} раз в ${label}`;
}

/** Map a catalog groupKey to a family group name, or null if no mapping. */
export function mapGroupKeyToFamily(
    groupKey: string | undefined,
    familyGroups: readonly string[]
): string | null {
    if (!groupKey) return null;
    // Built-in semantic groups auto-create on first use; map by known key.
    const known: Record<string, string> = {
        morning: 'Утро и вечер',
        study: 'Учёба',
        home: 'Дом и порядок',
        independence: 'Самостоятельность',
        health: 'Движение и здоровье',
        emotions: 'Общение и эмоции',
        habits: 'Полезные привычки',
        creativity: 'Творчество',
        family: 'Время с семьёй',
        privileges: 'Выбор и привилегии',
        joys: 'Маленькие радости',
        outings: 'Прогулки и развлечения',
        purchases: 'Покупки',
        biggoals: 'Большие цели',
    };
    const mapped = known[groupKey];
    if (mapped && familyGroups.includes(mapped)) return mapped;
    return null;
}

/** Build a family Task from a catalog template (copy operation). */
export function templateToTask(template: CatalogTaskTemplate, groupName: string | null): Task {
    return {
        id: generateLocalId(),
        name: template.title,
        coins: template.coins,
        groupName,
        icon: template.semanticGraphicKey ?? null,
        comment: template.comment ?? null,
        frequency: template.frequencyLimit != null
            ? { limit: template.frequencyLimit, period: (template.frequencyPeriod ?? 'week') as 'day' | 'week' | 'month' | 'year' }
            : null,
        isActive: true,
        sourceCatalogItemId: template.id,
    } as unknown as Task;
}

/** Build a family ShopItem from a catalog template (copy operation). */
export function templateToReward(template: CatalogRewardTemplate, groupName: string | null): ShopItem {
    return {
        id: generateLocalId(),
        name: template.title,
        price: template.price,
        groupName,
        icon: template.semanticGraphicKey ?? null,
        comment: template.comment ?? null,
        frequency: template.frequencyLimit != null
            ? { limit: template.frequencyLimit, period: (template.frequencyPeriod ?? 'week') as 'day' | 'week' | 'month' | 'year' }
            : null,
        isActive: true,
        sourceCatalogItemId: template.id,
    } as unknown as ShopItem;
}
