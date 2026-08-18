import type { Task } from '$lib/stores/app';

export interface TaskCatalogItemViewModel {
    id: number | string;
    title: string;
    amount: number;
    active: boolean;
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
    };
}
