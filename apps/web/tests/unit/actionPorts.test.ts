import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createProductionTaskActions } from '../../src/lib/telegram/services/taskActions';
import { createProductionRewardActions } from '../../src/lib/telegram/services/rewardActions';
import { createProductionRequestActions } from '../../src/lib/telegram/services/requestActions';
import { createProductionHistoryActions } from '../../src/lib/telegram/services/historyActions';
import { createProductionFamilyActions } from '../../src/lib/telegram/services/familyActions';
import { requestCoins, earnCoins, approveRequest, rejectRequest, deleteRequest } from '../../src/lib/services/api';
import { buyItem } from '../../src/lib/telegram/services/shopApi';
import { loadTelegramHistory } from '../../src/lib/services/telegramActivity';

vi.mock('../../src/lib/services/api', () => ({
    requestCoins: vi.fn(), requestCoinsWithNote: vi.fn(), earnCoins: vi.fn(), saveChildGroupOrder: vi.fn(),
    buyItem: vi.fn(), approveRequest: vi.fn(), rejectRequest: vi.fn(), deleteRequest: vi.fn(),
    adminAddChild: vi.fn(), adminSetChildActive: vi.fn(), adminAwardCoins: vi.fn(), updateFamilyLocale: vi.fn(),
    adminGetInactiveChildren: vi.fn(), adminGetChildTelegram: vi.fn(), adminCreateChildTelegramInvite: vi.fn(),
    adminUnlinkChildTelegram: vi.fn(), adminGetChildMagicLinkStatus: vi.fn(), adminIssueChildMagicLink: vi.fn(), adminRevokeChildMagicLink: vi.fn(),
    applyDataSnapshot: vi.fn(), refreshData: vi.fn(),
}));
vi.mock('../../src/lib/telegram/services/shopApi', () => ({ buyItem: vi.fn() }));
vi.mock('../../src/lib/services/telegramActivity', () => ({ loadTelegramHistory: vi.fn() }));
vi.mock('../../src/lib/services/save', () => ({ scheduleSave: vi.fn(async () => true) }));
vi.mock('../../src/lib/services/bootstrap', () => ({ applyDataSnapshot: vi.fn(), refreshData: vi.fn(), switchChild: vi.fn() }));

describe('scoped action ports', () => {
    beforeEach(() => vi.clearAllMocks());

    it('keeps task, reward, request and history production delegation typed', async () => {
        vi.mocked(requestCoins).mockResolvedValue({ ok: true, data: null });
        vi.mocked(earnCoins).mockResolvedValue(null);
        vi.mocked(buyItem).mockResolvedValue(null);
        vi.mocked(approveRequest).mockResolvedValue(false);
        vi.mocked(rejectRequest).mockResolvedValue(false);
        vi.mocked(deleteRequest).mockResolvedValue(false);
        vi.mocked(loadTelegramHistory).mockResolvedValue({ items: [], total: 0, page: 1, limit: 10 });

        await createProductionTaskActions().request({ taskId: 1, childId: 2, note: null });
        await createProductionTaskActions().complete({ taskId: 1, childId: 2 });
        await createProductionRewardActions().buy({ itemId: 3, childId: 2 });
        await createProductionRequestActions().approve(4, 2);
        await createProductionRequestActions().reject(5, 2);
        await createProductionRequestActions().cancel(6, 2);
        await createProductionHistoryActions().load('child', 1, 10);

        expect(requestCoins).toHaveBeenCalledWith(1, 2);
        expect(earnCoins).toHaveBeenCalledWith(1, 2);
        expect(buyItem).toHaveBeenCalledWith(3, 2);
        expect(approveRequest).toHaveBeenCalledWith(4, 2);
        expect(rejectRequest).toHaveBeenCalledWith(5, 2);
        expect(deleteRequest).toHaveBeenCalledWith(6, 2);
        expect(loadTelegramHistory).toHaveBeenCalledWith('child', 1, 10);
    });

    it('exposes family operations through one scoped owner', () => {
        const actions = createProductionFamilyActions();
        expect(actions).toMatchObject({ selectChild: expect.any(Function), addChild: expect.any(Function), setChildActive: expect.any(Function), getInactive: expect.any(Function) });
    });
});
