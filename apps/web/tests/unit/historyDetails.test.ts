import { describe, expect, it } from 'vitest';
import { historyMessages as russianHistoryMessages } from '../../src/lib/i18n/messages/ru/history';
import type { HistoryEntry, ShopItem, Task } from '../../src/lib/stores/app';
import { buildHistoryCatalog, resolveHistoryCard, type HistoryDetailsI18n } from '../../src/lib/components/app/sections/historyDetails';

function createRussianHistoryDetailsI18n(): HistoryDetailsI18n {
    return {
        t(key) {
            return russianHistoryMessages.model[key];
        },
    };
}

describe('resolveHistoryCard', () => {
    it('uses backend-provided title, description and group when present', () => {
        const entry = {
            id: 1,
            type: 'earn',
            amount: 12,
            title: 'Уроки без напоминаний',
            description: 'Уроки без напоминаний',
            comment: 'Собрать рюкзак и сделать домашнее задание',
            groupName: 'Учеба',
            moneyAmount: 0,
        } as HistoryEntry;

        const details = resolveHistoryCard(entry, buildHistoryCatalog(), createRussianHistoryDetailsI18n());

        expect(details.title).toBe('Уроки без напоминаний');
        expect(details.description).toBe('Собрать рюкзак и сделать домашнее задание');
        expect(details.group).toBe('Учеба');
        expect(details.coins).toBe(12);
        expect(details.moneyAmount).toBe(0);
    });

    it('fills purchase history details from the reward card when dto is sparse', () => {
        const reward: ShopItem = {
            id: 11,
            name: 'Небольшая косметика',
            price: 10,
            groupName: 'Красота',
            comment: 'Только после согласования с родителем',
            moneyLimit: 800,
        };
        const entry = {
            id: 2,
            type: 'spend',
            amount: 10,
            relatedId: 11,
        } as HistoryEntry;

        const details = resolveHistoryCard(entry, buildHistoryCatalog({ shopItems: [reward] }), createRussianHistoryDetailsI18n());

        expect(details.title).toBe('Небольшая косметика');
        expect(details.description).toBe('Только после согласования с родителем');
        expect(details.group).toBe('Красота');
        expect(details.coins).toBe(10);
        expect(details.moneyAmount).toBe(800);
    });

    it('fills earn history details from the task card when dto is sparse', () => {
        const task: Task = {
            id: 7,
            name: 'Уроки без напоминаний',
            coins: 12,
            groupName: 'Учеба',
            comment: 'Собрать рюкзак и сделать домашнее задание',
        };
        const entry = {
            id: 3,
            type: 'earn',
            amount: 12,
            relatedId: 7,
        } as HistoryEntry;

        const details = resolveHistoryCard(entry, buildHistoryCatalog({ tasks: [task] }), createRussianHistoryDetailsI18n());

        expect(details.title).toBe('Уроки без напоминаний');
        expect(details.description).toBe('Собрать рюкзак и сделать домашнее задание');
        expect(details.group).toBe('Учеба');
        expect(details.coins).toBe(12);
        expect(details.moneyAmount).toBe(0);
    });

    it('uses a localized fallback title when history data is sparse', () => {
        const details = resolveHistoryCard({ id: 4, type: 'admin', amount: 0 } as HistoryEntry, buildHistoryCatalog(), createRussianHistoryDetailsI18n());

        expect(details.title).toBe('Операция');
        expect(details.description).toBe('');
        expect(details.group).toBe('');
    });
});