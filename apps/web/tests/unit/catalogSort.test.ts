import { describe, expect, it } from 'vitest';
import { sortCatalogItems } from '../../src/lib/telegram/services/catalogSort';

type Item = { id: string; group: string; coins: number };

const items: Item[] = [
    { id: 'a', group: 'Дом', coins: 20 },
    { id: 'b', group: 'Учёба', coins: 5 },
    { id: 'c', group: 'Дом', coins: 10 },
    { id: 'd', group: '', coins: 1 },
];

describe('sortCatalogItems', () => {
    it('keeps the group order and sorts coins inside every group by ascending value', () => {
        expect(sortCatalogItems(items, 'group', ['Учёба', 'Дом'], (item) => item.group, (item) => item.coins).map((item) => item.id))
            .toEqual(['b', 'c', 'a', 'd']);
    });

    it('sorts the complete list by ascending coins when requested', () => {
        expect(sortCatalogItems(items, 'coins', ['Учёба', 'Дом'], (item) => item.group, (item) => item.coins).map((item) => item.id))
            .toEqual(['d', 'b', 'c', 'a']);
    });

    it('keeps all visible groups contiguous when no saved order exists', () => {
        expect(sortCatalogItems(items, 'group', ['Дом', 'Учёба'], (item) => item.group, (item) => item.coins).map((item) => item.id))
            .toEqual(['c', 'a', 'b', 'd']);
    });

    it('appends unsaved groups after the saved groups without interleaving them', () => {
        const namedItems = items.filter((item) => item.group);

        expect(sortCatalogItems(namedItems, 'group', ['Учёба'], (item) => item.group, (item) => item.coins).map((item) => item.id))
            .toEqual(['b', 'c', 'a']);
    });

    it('keeps equal coin values in their incoming order', () => {
        const equalCoins = [
            { id: 'first', group: 'Дом', coins: 10 },
            { id: 'second', group: 'Дом', coins: 10 },
        ];

        expect(sortCatalogItems(equalCoins, 'group', ['Дом'], (item) => item.group, (item) => item.coins).map((item) => item.id))
            .toEqual(['first', 'second']);
    });
});
