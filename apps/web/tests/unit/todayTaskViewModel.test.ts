import { describe, expect, it } from 'vitest';

import { buildTodayTaskSummary } from '$lib/services/todayTaskViewModel';
import type { Task } from '$lib/stores/app';

function task(overrides: Partial<Task>): Task {
    return {
        id: 1,
        name: 'Task',
        coins: 10,
        ...overrides,
    };
}

describe('buildTodayTaskSummary', () => {
    it('separates approved completions from pending requests', () => {
        const summary = buildTodayTaskSummary([
            task({
                periodProgress: {
                    period: 'day',
                    completed: 1,
                    pending: 1,
                    limit: 3,
                    remaining: 1,
                    available: true,
                    windowStart: '2026-07-18T00:00:00Z',
                    resetAt: '2026-07-19T00:00:00Z',
                },
            }),
        ]);

        expect(summary.completedCount).toBe(1);
        expect(summary.pendingCount).toBe(1);
        expect(summary.limitCount).toBe(3);
    });

    it('keeps unlimited active tasks available without adding a misleading denominator', () => {
        const unlimited = task({ id: 2, name: 'Read', coins: 5, periodProgress: null });
        const exhausted = task({
            id: 3,
            name: 'Clean',
            periodProgress: {
                period: 'day',
                completed: 1,
                pending: 0,
                limit: 1,
                remaining: 0,
                available: false,
                windowStart: '2026-07-18T00:00:00Z',
                resetAt: '2026-07-19T00:00:00Z',
            },
        });

        const summary = buildTodayTaskSummary([exhausted, unlimited]);

        expect(summary.trackedCount).toBe(1);
        expect(summary.limitCount).toBe(1);
        expect(summary.availableCount).toBe(1);
        expect(summary.nextTask).toBe(unlimited);
    });
});
