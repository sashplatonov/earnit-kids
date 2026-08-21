import { describe, expect, it } from 'vitest';
import type { Request } from '../../src/lib/stores/app';
import { requestKind } from '../../src/lib/components/telegram/telegramRequestKind';

const requests: Request[] = [
    { id: 1, requestType: 'task_completion', taskName: 'Clean room', taskGroup: 'Home', coins: 20, status: 'approved', createdAt: '2026-08-20T09:00:00Z' },
    { id: 2, requestType: 'shop_purchase', itemName: 'Game time', itemGroup: 'Fun', amount: 30, status: 'pending', createdAt: '2026-08-19T09:00:00Z' },
    { id: 3, requestType: 'task_completion', taskName: 'Read', coins: 10, status: 'pending', createdAt: '2026-08-20T10:00:00Z' },
    { id: 4, requestType: 'unknown', title: 'Legacy request', amount: 5, status: 'mystery', createdAt: null },
];

function sortRequests(input: Request[]): Request[] {
    return [...input].sort((a, b) => {
        const aPending = a.status === 'pending';
        const bPending = b.status === 'pending';
        if (aPending !== bPending) return aPending ? -1 : 1;
        const aTime = a.createdAt ? new Date(a.createdAt).getTime() : 0;
        const bTime = b.createdAt ? new Date(b.createdAt).getTime() : 0;
        return bTime - aTime;
    });
}

function statusTone(status: string): 'pending' | 'approved' | 'rejected' | 'cancelled' | 'neutral' {
    if (status === 'approved' || status === 'rejected' || status === 'cancelled' || status === 'pending') return status;
    return 'neutral';
}

describe('Telegram request presentation contract', () => {
    it('orders pending requests first and newest-first within each status group', () => {
        expect(sortRequests(requests).map(({ id }) => id)).toEqual([3, 2, 1, 4]);
    });

    it('keeps task/reward metadata and amount direction deterministic', () => {
        expect(requestKind(requests[0])).toBe('task');
        expect(requestKind(requests[1])).toBe('reward');
        expect({ group: requests[0].taskGroup, kind: requestKind(requests[0]), amount: `+${requests[0].coins}` })
            .toEqual({ group: 'Home', kind: 'task', amount: '+20' });
        expect({ group: requests[1].itemGroup, kind: requestKind(requests[1]), amount: `-${requests[1].amount}` })
            .toEqual({ group: 'Fun', kind: 'reward', amount: '-30' });
    });

    it('maps known statuses and unknown backend values to a safe neutral tone', () => {
        expect(['pending', 'approved', 'rejected', 'cancelled'].map(statusTone)).toEqual(['pending', 'approved', 'rejected', 'cancelled']);
        expect(statusTone('mystery')).toBe('neutral');
        expect(requestKind(requests[3])).toBe('task');
    });
});
