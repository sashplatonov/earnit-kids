import { beforeEach, describe, expect, it } from 'vitest';
import { get } from 'svelte/store';
import { appStore } from '../../src/lib/stores/app';
import { shopItems } from '../../src/lib/telegram/stores/shopItems';
import { createLiveCoinShopDemoSession, liveCoinShopDemoFixture } from '../../src/lib/features/live-demo/liveCoinShopDemoSession';

describe('live coin shop demo session', () => {
    let session: ReturnType<typeof createLiveCoinShopDemoSession>;

    beforeEach(() => {
        session = createLiveCoinShopDemoSession();
        session.initialize();
    });

    it('initializes a deterministic child fixture with affordable and next-goal rewards', () => {
        const state = get(appStore);
        const items = get(shopItems);

        expect(state.currentChildId).toBe(liveCoinShopDemoFixture.childId);
        expect(state.balance).toBe(75);
        expect(state.children).toHaveLength(2);
        expect(state.requests.map((request) => request.status)).toEqual(['pending', 'approved', 'rejected']);
        expect(items.filter((item) => item.isActive !== false)).toHaveLength(3);
        expect(items.map((item) => item.groupName)).toEqual(['Treats', 'Experiences', 'Treats', 'Learning']);
        expect(items.find((item) => item.id === liveCoinShopDemoFixture.affordableRewardId)?.price).toBeLessThan(state.balance);
        expect(items.find((item) => item.id === liveCoinShopDemoFixture.unaffordableRewardId)?.price).toBeGreaterThan(state.balance);
    });

    it('builds the same complete fixture with Russian presentation data', () => {
        const russianSession = createLiveCoinShopDemoSession('ru');
        russianSession.initialize();

        expect(get(appStore).children.map((child) => child.nickname)).toEqual(['Мия', 'Лео']);
        expect(get(appStore).tasks.map((task) => task.name)).toContain('Заправить кровать');
        expect(get(shopItems).map((item) => item.groupName)).toEqual(['Угощения', 'Впечатления', 'Угощения', 'Учёба']);
        expect(get(shopItems).map((item) => item.name)).toContain('Мороженое');
    });

    it('creates one pending request with and without a note without changing balance', async () => {
        const result = await session.actions.request({ itemId: liveCoinShopDemoFixture.affordableRewardId, childId: liveCoinShopDemoFixture.childId, note: 'Saturday afternoon' });

        expect(result).toMatchObject({ ok: true });
        expect(get(appStore).balance).toBe(75);
        expect(get(appStore).requests).toContainEqual(expect.objectContaining({
            requestType: 'shop_purchase',
            itemId: liveCoinShopDemoFixture.affordableRewardId,
            itemName: 'Ice cream',
            note: 'Saturday afternoon',
            childId: liveCoinShopDemoFixture.childId,
            status: 'pending',
        }));

        session.reset();
        const noNoteResult = await session.actions.request({ itemId: liveCoinShopDemoFixture.affordableRewardId, childId: liveCoinShopDemoFixture.childId, note: null });
        expect(noNoteResult).toMatchObject({ ok: true });
        expect(get(appStore).requests.at(-1)?.note).toBeNull();
    });

    it('rejects absent, inactive, unaffordable and duplicate requests without mutation', async () => {
        const initial = get(appStore);
        for (const itemId of ['missing', liveCoinShopDemoFixture.inactiveRewardId, liveCoinShopDemoFixture.unaffordableRewardId]) {
            const result = await session.actions.request({ itemId, childId: liveCoinShopDemoFixture.childId, note: null });
            expect(result.ok).toBe(false);
            expect(get(appStore)).toEqual(initial);
        }

        await session.actions.request({ itemId: liveCoinShopDemoFixture.affordableRewardId, childId: liveCoinShopDemoFixture.childId, note: null });
        const afterValid = get(appStore);
        const duplicate = await session.actions.request({ itemId: liveCoinShopDemoFixture.affordableRewardId, childId: liveCoinShopDemoFixture.childId, note: 'Again' });
        expect(duplicate.ok).toBe(false);
        expect(get(appStore)).toEqual(afterValid);
    });

    it('resets and tears down demo-owned state, and sessions do not share references', () => {
        const firstItem = get(shopItems)[0];
        firstItem.name = 'Changed locally';
        session.reset();
        expect(get(shopItems)[0].name).toBe('Ice cream');

        const secondSession = createLiveCoinShopDemoSession();
        secondSession.initialize();
        expect(get(shopItems)[0].name).toBe('Ice cream');
        secondSession.teardown();
        expect(get(shopItems)).toEqual([]);
        expect(get(appStore).currentChildId).toBeNull();
    });
});
