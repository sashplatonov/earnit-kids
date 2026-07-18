import type { Task } from '$lib/stores/app';

export interface TodayTaskSummary {
    trackedCount: number;
    completedCount: number;
    pendingCount: number;
    limitCount: number;
    availableCount: number;
    nextTask: Task | null;
}

export function buildTodayTaskSummary(tasks: Task[]): TodayTaskSummary {
    const tracked = tasks.filter((task) => task.periodProgress != null);
    const completedCount = tracked.reduce((total, task) => total + (task.periodProgress?.completed ?? 0), 0);
    const pendingCount = tracked.reduce((total, task) => total + (task.periodProgress?.pending ?? 0), 0);
    const limitCount = tracked.reduce((total, task) => total + (task.periodProgress?.limit ?? 0), 0);
    const available = tasks.filter((task) => task.isActive !== false && task.periodProgress?.available !== false);
    const nextTask = [...available].sort((first, second) => {
        const firstProgress = first.periodProgress?.completed ?? 0;
        const secondProgress = second.periodProgress?.completed ?? 0;
        if (firstProgress !== secondProgress) return firstProgress - secondProgress;
        return (second.coins ?? 0) - (first.coins ?? 0);
    })[0] ?? null;

    return {
        trackedCount: tracked.length,
        completedCount,
        pendingCount,
        limitCount,
        availableCount: available.length,
        nextTask,
    };
}
