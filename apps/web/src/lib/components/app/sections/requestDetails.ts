import type { Request, ShopItem, Task } from '$lib/stores/app';

export interface RequestCatalog {
    tasks?: Task[];
    shopItems?: ShopItem[];
    baseTasks?: Task[];
    baseProducts?: ShopItem[];
}

export interface RequestCatalogLookups {
    taskLookup: Map<string, Task>;
    itemLookup: Map<string, ShopItem>;
}

export interface RequestCardDetails {
    title: string;
    description: string;
    group: string;
    coins: number;
    moneyAmount: number;
    isPurchase: boolean;
}

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

export function isPurchaseRequest(request: Request): boolean {
    return request.requestType === 'shop_purchase' || request.itemId != null;
}

export function buildRequestCatalog(catalog: RequestCatalog = {}): RequestCatalogLookups {
    return {
        taskLookup: buildLookup([...(catalog.tasks ?? []), ...(catalog.baseTasks ?? [])]),
        itemLookup: buildLookup([...(catalog.shopItems ?? []), ...(catalog.baseProducts ?? [])]),
    };
}

function findTask(request: Request, lookups: RequestCatalogLookups): Task | undefined {
    const taskId = toKey(request.taskId);
    return taskId ? lookups.taskLookup.get(taskId) : undefined;
}

function findItem(request: Request, lookups: RequestCatalogLookups): ShopItem | undefined {
    const itemId = toKey(request.itemId ?? (isPurchaseRequest(request) ? request.taskId : null));
    return itemId ? lookups.itemLookup.get(itemId) : undefined;
}

export function resolveRequestCard(request: Request, lookups: RequestCatalogLookups): RequestCardDetails {
    const purchase = isPurchaseRequest(request);
    const task = findTask(request, lookups);
    const item = findItem(request, lookups);

    return {
        title: firstNonBlank(
            request['itemName'],
            request['taskName'],
            purchase ? item?.name : task?.name,
            purchase ? task?.name : item?.name,
            purchase ? 'Покупка' : 'Задание'
        ),
        description: firstNonBlank(
            request['description'],
            request['taskComment'],
            request['itemComment'],
            request['comment'],
            purchase ? item?.comment : task?.comment,
            purchase ? task?.comment : item?.comment,
            'Без описания'
        ),
        group: firstNonBlank(
            request['taskGroup'],
            request['itemGroup'],
            request['group'],
            purchase ? item?.groupName : task?.groupName,
            purchase ? task?.groupName : item?.groupName,
            'Без группы'
        ),
        coins: toNumber(request['coins'] ?? request.amount ?? (purchase ? item?.price : task?.coins) ?? 0),
        moneyAmount: toNumber(request['moneyAmount'] ?? (purchase ? item?.moneyLimit : task?.moneyLimit) ?? 0),
        isPurchase: purchase,
    };
}