import { historyMessages as englishHistoryMessages } from '$lib/i18n/messages/en/history';
import type { HistoryEntry, ShopItem, Task } from '$lib/stores/app';

export interface HistoryCatalog {
    tasks?: Task[];
    shopItems?: ShopItem[];
    baseTasks?: Task[];
}

export interface HistoryCatalogLookups {
    taskLookup: Map<string, Task>;
    itemLookup: Map<string, ShopItem>;
}

export interface HistoryCardDetails {
    title: string;
    description: string;
    group: string;
    coins: number;
    moneyAmount: number;
    isPurchase: boolean;
}

type ActivityModelMessageKey = keyof typeof englishHistoryMessages.model;

export interface HistoryDetailsI18n {
    t(key: ActivityModelMessageKey): string;
}

const DEFAULT_HISTORY_DETAILS_I18N: HistoryDetailsI18n = {
    t(key) {
        return englishHistoryMessages.model[key];
    },
};

function asText(value: unknown): string {
    return typeof value === 'string' ? value.trim() : '';
}

function firstNonBlank(...values: unknown[]): string {
    for (const value of values) {
        const text = asText(value);
        if (text) {
            return text;
        }
    }
    return '';
}

function toKey(value: unknown): string {
    return value == null ? '' : String(value);
}

function toNumber(value: unknown): number {
    const number = Number(value ?? 0);
    return Number.isFinite(number) ? number : 0;
}

function buildLookup<T extends { id?: unknown }>(items: T[]): Map<string, T> {
    const lookup = new Map<string, T>();
    for (const item of items) {
        const key = toKey(item.id);
        if (key && !lookup.has(key)) {
            lookup.set(key, item);
        }
    }
    return lookup;
}

function isPurchaseHistory(entry: HistoryEntry): boolean {
    return entry.type === 'purchase' || entry.type === 'spend' || entry.itemId != null;
}

function findTask(entry: HistoryEntry, lookups: HistoryCatalogLookups): Task | undefined {
    const taskId = toKey(entry.taskId ?? (!isPurchaseHistory(entry) ? entry.relatedId : null));
    return taskId ? lookups.taskLookup.get(taskId) : undefined;
}

function findItem(entry: HistoryEntry, lookups: HistoryCatalogLookups): ShopItem | undefined {
    const itemId = toKey(entry.itemId ?? (isPurchaseHistory(entry) ? entry.relatedId : null));
    return itemId ? lookups.itemLookup.get(itemId) : undefined;
}

export function buildHistoryCatalog(catalog: HistoryCatalog = {}): HistoryCatalogLookups {
    return {
        taskLookup: buildLookup([...(catalog.tasks ?? []), ...(catalog.baseTasks ?? [])]),
        itemLookup: buildLookup([...(catalog.shopItems ?? [])]),
    };
}

export function resolveHistoryCard(
    entry: HistoryEntry,
    lookups: HistoryCatalogLookups,
    i18n: HistoryDetailsI18n = DEFAULT_HISTORY_DETAILS_I18N,
): HistoryCardDetails {
    const purchase = isPurchaseHistory(entry);
    const task = findTask(entry, lookups);
    const item = findItem(entry, lookups);

    return {
        title: firstNonBlank(
            entry.title,
            purchase ? entry.itemName : entry.taskName,
            entry.description,
            purchase ? item?.name : task?.name,
            purchase ? task?.name : item?.name,
            i18n.t('historyOperationFallback')
        ),
        description: firstNonBlank(
            entry.comment,
            purchase ? item?.comment : task?.comment,
            purchase ? task?.comment : item?.comment
        ),
        group: firstNonBlank(
            entry.groupName,
            entry['group'],
            purchase ? item?.groupName : task?.groupName,
            purchase ? task?.groupName : item?.groupName
        ),
        coins: toNumber(entry.amount ?? (purchase ? item?.price : task?.coins) ?? 0),
        moneyAmount: toNumber(entry.moneyAmount ?? (purchase ? item?.moneyLimit : task?.moneyLimit) ?? 0),
        isPurchase: purchase,
    };
}