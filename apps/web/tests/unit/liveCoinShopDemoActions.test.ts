import { beforeEach, describe, expect, it } from 'vitest';
import { get } from 'svelte/store';
import { appStore } from '../../src/lib/stores/app';
import { shopItems } from '../../src/lib/telegram/stores/shopItems';
import { createLiveCoinShopDemoSession, liveCoinShopDemoFixture } from '../../src/lib/features/live-demo/liveCoinShopDemoSession';

describe('live coin shop demo actions', () => {
    let session: ReturnType<typeof createLiveCoinShopDemoSession>;

    beforeEach(() => {
        session = createLiveCoinShopDemoSession();
        session.initialize();
    });

    it('approves a request atomically across child balance, request and history', async () => {
        const result = await session.actions.approveRequest(liveCoinShopDemoFixture.pendingRequestId);
        expect(result.ok).toBe(true);
        expect(get(appStore).children.find((child) => child.id === liveCoinShopDemoFixture.secondChildId)?.balance).toBe(100);
        expect(get(appStore).requests.find((request) => request.id === liveCoinShopDemoFixture.pendingRequestId)?.status).toBe('approved');
        expect(get(appStore).history[0]).toMatchObject({ type: 'purchase', amount: -45, itemId: 'live-demo-reward-book' });
    });

    it('rejects invalid coin mutations without changing the snapshot', async () => {
        const before = session.snapshot();
        await session.actions.spendCoins({ childId: liveCoinShopDemoFixture.childId, amount: 1000 });
        expect(session.snapshot()).toEqual(before);
    });

    it('keeps child data isolated and restores all mutations on reset', async () => {
        await session.actions.awardCoins({ childId: liveCoinShopDemoFixture.childId, amount: 25, description: 'Bonus' });
        await session.actions.createTask({ name: 'Feed the cat', coins: 12, groupName: 'Home' });
        await session.actions.createReward({ name: 'Board game', price: 60, groupName: 'Experiences' });
        expect(get(appStore).balance).toBe(100);
        expect(get(appStore).tasks).toHaveLength(4);
        expect(get(shopItems)).toHaveLength(5);

        await session.actions.selectChild(liveCoinShopDemoFixture.secondChildId);
        expect(get(appStore).balance).toBe(145);
        session.reset();
        expect(get(appStore).balance).toBe(75);
        expect(get(appStore).tasks).toHaveLength(3);
        expect(get(shopItems)).toHaveLength(4);
    });

    it('supports catalog add, terminal request states and group operations', async () => {
        await session.actions.addCatalogReward({ id: 'live-demo-catalog-reward' });
        expect(get(shopItems).some((item) => item.name === 'Choose a dessert')).toBe(true);
        await session.actions.setGroupVisibility('Treats', true);
        expect(get(appStore).children.every((child) => child.hiddenShopGroupOrder?.includes('Treats'))).toBe(true);
        await session.actions.rejectRequest(liveCoinShopDemoFixture.pendingRequestId);
        expect(get(appStore).requests.find((request) => request.id === liveCoinShopDemoFixture.pendingRequestId)?.status).toBe('rejected');
    });
});
