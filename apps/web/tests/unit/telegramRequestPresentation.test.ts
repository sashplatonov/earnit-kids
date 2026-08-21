import { describe, expect, it } from 'vitest';
import type { Request } from '../../src/lib/stores/app';
import { presentRequest, requestStatus, sortRequestPresentations } from '../../src/lib/components/telegram/telegramRequestPresentation';

const requests: Request[] = [
    { id: 1, requestType: 'task_completion', taskName: 'Clean room', taskGroup: 'Home', coins: 20, status: 'approved', createdAt: '2026-08-20T09:00:00Z' },
    { id: 2, requestType: 'shop_purchase', itemName: 'Game time', itemGroup: 'Fun', amount: 30, status: 'pending', createdAt: '2026-08-19T09:00:00Z' },
    { id: 3, requestType: 'task_completion', taskName: 'Read', coins: 10, status: 'pending', createdAt: '2026-08-20T10:00:00Z' },
    { id: 4, requestType: 'unknown', title: 'Legacy request', amount: 5, status: 'mystery', createdAt: null },
];

const translator = {
    kindLabel: (kind: 'task' | 'reward') => kind,
    statusLabel: (status: string) => status,
    metadata: (request: Request, _kind: 'task' | 'reward', kindLabel: string) => `${request.taskGroup || request.itemGroup || ''} ${kindLabel}`.trim(),
};

describe('Telegram request presentation contract', () => {
    it('orders pending requests first and newest-first within each status group', () => {
        expect(sortRequestPresentations(requests.map((request) => presentRequest(request, translator))).map(({ request }) => request.id)).toEqual([3, 2, 1, 4]);
    });

    it('keeps task/reward metadata and amount direction deterministic', () => {
        const task = presentRequest(requests[0], translator);
        const reward = presentRequest(requests[1], translator);
        expect({ group: requests[0].taskGroup, kind: task.kind, amount: `${task.amountSign}${task.amount}` })
            .toEqual({ group: 'Home', kind: 'task', amount: '+20' });
        expect({ group: requests[1].itemGroup, kind: reward.kind, amount: `${reward.amountSign}${reward.amount}` })
            .toEqual({ group: 'Fun', kind: 'reward', amount: '-30' });
    });

    it('maps known statuses and unknown backend values to a safe neutral tone', () => {
        expect(['pending', 'approved', 'rejected', 'cancelled'].map(requestStatus)).toEqual(['pending', 'approved', 'rejected', 'cancelled']);
        expect(requestStatus('mystery')).toBe('unknown');
        expect(presentRequest(requests[3], translator).statusLabel).toBe('unknown');
    });
});
