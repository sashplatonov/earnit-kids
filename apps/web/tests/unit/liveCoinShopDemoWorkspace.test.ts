import { describe, expect, it } from 'vitest';
import { get } from 'svelte/store';
import { appStore } from '../../src/lib/stores/app';
import { createLiveCoinShopDemoSession } from '../../src/lib/features/live-demo/liveCoinShopDemoSession';
import { createLiveCoinShopDemoWorkspace } from '../../src/lib/features/live-demo/liveCoinShopDemoWorkspace';

describe('live coin shop demo workspace composition', () => {
    it('initializes and switches children without a transport call', async () => {
        const session = createLiveCoinShopDemoSession();
        const workspace = createLiveCoinShopDemoWorkspace(session);
        session.initialize();

        expect(await workspace.workspace.initialize()).toBe(true);
        await workspace.workspace.switchChild('live-demo-child-2');
        expect(get(appStore)).toMatchObject({ currentChildId: 'live-demo-child-2', balance: 145, permission: 'family_admin' });
        expect(await workspace.workspace.refresh()).toBe(true);
    });

    it('keeps reset and view ownership at the composition boundary', async () => {
        const session = createLiveCoinShopDemoSession();
        const workspace = createLiveCoinShopDemoWorkspace(session);
        session.initialize();
        await workspace.family.awardCoins('live-demo-child', 10, 'Demo bonus');
        expect(get(appStore).balance).toBe(85);
        session.reset();
        expect(get(appStore).balance).toBe(75);
    });

    it('routes reward grants through the in-memory session', async () => {
        const session = createLiveCoinShopDemoSession();
        const workspace = createLiveCoinShopDemoWorkspace(session);
        session.initialize();

        expect(await workspace.rewards.buy({ itemId: 'live-demo-reward-ice-cream', childId: 'live-demo-child' })).toBeTruthy();
        expect(get(appStore).balance).toBe(45);
    });
});
