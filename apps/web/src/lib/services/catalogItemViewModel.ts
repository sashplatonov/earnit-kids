import type { ShopItem, Task, TaskPeriodProgress } from '$lib/stores/app';

export interface TaskCatalogItemViewModel {
    id: number | string;
    title: string;
    amount: number;
    active: boolean;
    progress: TaskPeriodProgress | null;
}

export interface ShopCatalogItemViewModel {
    id: number | string;
    title: string;
    amount: number;
    active: boolean;
    affordable: boolean;
    missing: number;
}

function toNonNegativeNumber(value: unknown): number {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? Math.max(0, parsed) : 0;
}

export function buildTaskCatalogItemViewModel(task: Task): TaskCatalogItemViewModel {
    return {
        id: task.id,
        title: String(task.title ?? task.name ?? ''),
        amount: toNonNegativeNumber(task.coins),
        active: task.isActive !== false,
        progress: task.periodProgress ?? null,
    };
}

export function buildShopCatalogItemViewModel(item: ShopItem, balance: number): ShopCatalogItemViewModel {
    const amount = toNonNegativeNumber(item.price);
    const availableBalance = toNonNegativeNumber(balance);
    return {
        id: item.id,
        title: String(item.name ?? ''),
        amount,
        active: item.isActive !== false,
        affordable: availableBalance >= amount,
        missing: Math.max(0, amount - availableBalance),
    };
}
