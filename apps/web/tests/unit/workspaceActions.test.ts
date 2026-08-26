import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createProductionWorkspaceActions, type WorkspaceActions } from '../../src/lib/features/workspace/workspaceActions';
import { applyDataSnapshot, initializeFromServer, refreshData, switchChild } from '../../src/lib/services/bootstrap';
import { loadTelegramHistory } from '../../src/lib/services/telegramActivity';

vi.mock('../../src/lib/services/bootstrap', () => ({
    applyDataSnapshot: vi.fn(),
    initializeFromServer: vi.fn(),
    refreshData: vi.fn(),
    switchChild: vi.fn(),
}));

vi.mock('../../src/lib/services/telegramActivity', () => ({
    loadTelegramHistory: vi.fn(),
}));

describe('workspace actions', () => {
    beforeEach(() => vi.clearAllMocks());

    it('delegates the production lifecycle and history contract', async () => {
        vi.mocked(initializeFromServer).mockResolvedValue(true);
        vi.mocked(refreshData).mockResolvedValue(false);
        vi.mocked(loadTelegramHistory).mockResolvedValue({ items: [], total: 0, page: 2, limit: 10 });
        const actions = createProductionWorkspaceActions();
        const snapshot = { balance: 12 };

        await expect(actions.initialize()).resolves.toBe(true);
        await expect(actions.refresh(true)).resolves.toBe(false);
        actions.applySnapshot(snapshot);
        await actions.switchChild('child-2');
        await expect(actions.loadHistory({ childId: 'child-2', page: 2, limit: 10 })).resolves.toMatchObject({ page: 2 });

        expect(initializeFromServer).toHaveBeenCalledOnce();
        expect(refreshData).toHaveBeenCalledWith(true);
        expect(applyDataSnapshot).toHaveBeenCalledWith(snapshot);
        expect(switchChild).toHaveBeenCalledWith('child-2');
        expect(loadTelegramHistory).toHaveBeenCalledWith('child-2', 2, 10);
    });

    it('allows an independently scoped override without changing the production port', () => {
        const production = createProductionWorkspaceActions();
        const demo: WorkspaceActions = {
            initialize: vi.fn(),
            refresh: vi.fn(),
            applySnapshot: vi.fn(),
            switchChild: vi.fn(),
            loadHistory: vi.fn(),
        };

        expect(demo).not.toBe(production);
        expect(demo.initialize).not.toBe(production.initialize);
        expect(production.initialize).toBe(initializeFromServer);
    });
});
