import { describe, expect, it } from 'vitest';
import {
    buildInitialState,
    normalizeAuthResponse,
    normalizeChild,
    normalizeHistoryEntry,
    normalizeRequest,
    normalizeShopItem,
    normalizeTask,
} from '../../src/lib/services/serverContract';

describe('normalizeChild', () => {
    it('maps name to nickname for child switcher compatibility', () => {
        const child = normalizeChild({ id: 3, name: 'Маша', balance: 0 });

        expect(child.name).toBe('Маша');
        expect(child.nickname).toBe('Маша');
    });

    it('normalizes parent and child group order arrays', () => {
        const child = normalizeChild({
            id: 3,
            name: 'Маша',
            balance: 0,
            task_group_order: [' Дом ', 'Учеба', 'Дом'],
            child_shop_group_order: ['Хочу', ' Потом '],
        });

        expect(child.taskGroupOrder).toEqual(['Дом', 'Учеба']);
        expect(child.childShopGroupOrder).toEqual(['Хочу', 'Потом']);
    });
});

describe('normalizeShopItem', () => {
    it('preserves name and price from canonical fields', () => {
        const item = normalizeShopItem({ name: 'Мороженое', price: 20, comment: 'Шарик' });
        expect(item.name).toBe('Мороженое');
        expect(item.price).toBe(20);
        expect(item.title).toBe('Мороженое');
        expect(item.coins).toBe(20);
        expect(item.comment).toBe('Шарик');
    });

    it('maps title -> name when name is absent', () => {
        const item = normalizeShopItem({ title: 'Поход в кино', price: 50 });
        expect(item.name).toBe('Поход в кино');
        expect(item.title).toBe('Поход в кино');
    });

    it('maps coins -> price when price is absent', () => {
        const item = normalizeShopItem({ name: 'Лишний час', coins: 30 });
        expect(item.price).toBe(30);
        expect(item.coins).toBe(30);
    });

    it('normalizes groupName from group field', () => {
        const item = normalizeShopItem({ name: 'Test', price: 10, group: 'Развлечения' });
        expect(item.groupName).toBe('Развлечения');
    });

    it('normalizes moneyLimit from money_limit field', () => {
        const item = normalizeShopItem({ name: 'Test', price: 10, money_limit: 500 });
        expect(item.moneyLimit).toBe(500);
    });

    it('keeps frequency payload for shop item limits', () => {
        const item = normalizeShopItem({ name: 'Test', price: 10, frequency: { limit: 2, period: 'day' } });
        expect(item.frequency).toEqual({ limit: 2, period: 'day' });
    });

    it('sets comment to null when absent', () => {
        const item = normalizeShopItem({ name: 'Test', price: 5 });
        expect(item.comment).toBeNull();
    });

    it('handles empty input gracefully', () => {
        const item = normalizeShopItem({});
        expect(item.name).toBe('');
        expect(item.price).toBe(0);
        expect(item.title).toBe('');
        expect(item.coins).toBe(0);
    });
});

describe('normalizeTask', () => {
    it('preserves name and coins from canonical fields', () => {
        const task = normalizeTask({ name: 'Сделать уборку', coins: 15 });
        expect(task.name).toBe('Сделать уборку');
        expect(task.coins).toBe(15);
    });

    it('maps title -> name when name is absent', () => {
        const task = normalizeTask({ title: 'Полить цветы', coins: 5 });
        expect(task.name).toBe('Полить цветы');
    });

    it('maps price -> coins when coins is absent', () => {
        const task = normalizeTask({ name: 'Помочь с готовкой', price: 8 });
        expect(task.coins).toBe(8);
    });
});

describe('normalizeHistoryEntry', () => {
    it('assigns taskId for earn-type entries', () => {
        const entry = normalizeHistoryEntry({ type: 'earn', relatedId: 42, amount: 10 });
        expect(entry.taskId).toBe(42);
    });

    it('assigns itemId for spend-type entries', () => {
        const entry = normalizeHistoryEntry({ type: 'spend', relatedId: 7, amount: -20 });
        expect(entry.itemId).toBe(7);
    });

    it('normalizes createdAt from created_at', () => {
        const ts = '2026-04-18T12:00:00Z';
        const entry = normalizeHistoryEntry({ type: 'earn', amount: 5, created_at: ts });
        expect(entry.createdAt).toBe(ts);
    });

    it('keeps backend title, group and comment fields', () => {
        const entry = normalizeHistoryEntry({
            type: 'spend',
            title: 'Велосипед',
            description: 'Велосипед',
            groupName: 'Транспорт',
            comment: 'Только на выходных',
        });

        expect(entry.title).toBe('Велосипед');
        expect(entry.groupName).toBe('Транспорт');
        expect(entry.comment).toBe('Только на выходных');
    });
});

describe('normalizeRequest', () => {
    it('normalizes createdAt from created_at', () => {
        const ts = '2026-04-17T10:00:00Z';
        const req = normalizeRequest({ requestType: 'purchase', status: 'pending', created_at: ts });
        expect(req.createdAt).toBe(ts);
    });

    it('maps generic backend display fields', () => {
        const req = normalizeRequest({
            requestType: 'shop_purchase',
            status: 'pending',
            title: 'Набор красок',
            description: 'Только после уроков',
            groupName: 'Творчество',
        });

        expect(req.title).toBe('Набор красок');
        expect(req.description).toBe('Только после уроков');
        expect(req.groupName).toBe('Творчество');
    });
});

describe('buildInitialState', () => {
    it('preserves family rules from the backend payload', () => {
        const state = buildInitialState(
            { isAdmin: true, balance: 12, rules: 'Finish homework first', permission: 'family_admin' },
            { tasks: [], products: [] }
        );

        expect(state.rules).toBe('Finish homework first');
        expect(state.balance).toBe(12);
        expect(state.isAdmin).toBe(true);
        expect(state.permission).toBe('family_admin');
    });
});

describe('normalizeAuthResponse', () => {
    it('normalizes chooser-required auth payloads with membership permissions', () => {
        const response = normalizeAuthResponse({
            success: true,
            selectionRequired: true,
            familyChoices: [
                { familyId: 'f-1', familyName: 'Winter House', permission: 'viewer' },
                { familyId: 'f-2', familyName: 'Summer House', permission: 'family_admin' },
            ],
        });

        expect(response.selectionRequired).toBe(true);
        expect(response.familyChoices).toEqual([
            { familyId: 'f-1', familyName: 'Winter House', permission: 'viewer' },
            { familyId: 'f-2', familyName: 'Summer House', permission: 'family_admin' },
        ]);
    });
});
