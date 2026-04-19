import { describe, expect, it } from 'vitest';
import { normalizeShopItem, normalizeTask, normalizeHistoryEntry, normalizeRequest } from '../../src/lib/services/serverContract';

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
});

describe('normalizeRequest', () => {
    it('normalizes createdAt from created_at', () => {
        const ts = '2026-04-17T10:00:00Z';
        const req = normalizeRequest({ requestType: 'purchase', status: 'pending', created_at: ts });
        expect(req.createdAt).toBe(ts);
    });
});
