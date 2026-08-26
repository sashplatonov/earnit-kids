import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createProductionRewardRequestActions, type RewardRequestActions } from '../../src/lib/telegram/services/rewardRequestActions';
import { requestItem, requestItemWithNote } from '../../src/lib/telegram/services/shopApi';

vi.mock('../../src/lib/telegram/services/shopApi', () => ({
    requestItem: vi.fn(),
    requestItemWithNote: vi.fn(),
}));

describe('reward request actions', () => {
    beforeEach(() => vi.clearAllMocks());

    it('delegates note-free requests to the production shop API action', async () => {
        vi.mocked(requestItem).mockResolvedValue({ ok: true, data: null });
        const actions = createProductionRewardRequestActions();

        await expect(actions.request({ itemId: 7, childId: 'child-2', note: null })).resolves.toEqual({ ok: true, data: null });
        expect(requestItem).toHaveBeenCalledWith(7, 'child-2');
        expect(requestItemWithNote).not.toHaveBeenCalled();
    });

    it('delegates noted requests to the note-aware production shop API action', async () => {
        vi.mocked(requestItemWithNote).mockResolvedValue({ ok: false, error: 'failed', errorCode: 'ERROR', status: 400 });
        const actions = createProductionRewardRequestActions();

        await expect(actions.request({ itemId: 'reward-7', childId: 2, note: 'Saturday' })).resolves.toEqual({ ok: false, error: 'failed', errorCode: 'ERROR', status: 400 });
        expect(requestItemWithNote).toHaveBeenCalledWith('reward-7', 'Saturday', 2);
        expect(requestItem).not.toHaveBeenCalled();
    });

    it('keeps an injected action scoped to the component context contract', () => {
        const production = createProductionRewardRequestActions();
        const override: RewardRequestActions = { request: vi.fn() };

        expect(override).not.toBe(production);
        expect(override.request).not.toBe(production.request);
    });
});
