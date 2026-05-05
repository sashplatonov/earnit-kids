import { describe, expect, it } from 'vitest';
import {
    applyGroupOrderToChildren,
    getEffectiveGroupOrder,
    isNoopGroupDrop,
    moveGroup,
    normalizeGroupLabel,
    orderGroups,
    reorderGroupsBySlot,
    sortItemsByGroup,
} from '../../src/lib/services/groupOrder';

describe('normalizeGroupLabel', () => {
    it('falls back to the ungrouped label for empty values', () => {
        expect(normalizeGroupLabel(null)).toBe('Без группы');
        expect(normalizeGroupLabel('   ')).toBe('Без группы');
    });
});

describe('orderGroups', () => {
    it('prioritizes the saved order and appends new groups afterwards', () => {
        expect(orderGroups(['Дом', 'Учеба', 'Спорт'], ['Учеба', 'Дом'])).toEqual(['Учеба', 'Дом', 'Спорт']);
    });
});

describe('sortItemsByGroup', () => {
    it('clusters items by the configured group order while keeping intra-group order stable', () => {
        const items = [
            { id: 1, groupName: 'Дом' },
            { id: 2, groupName: 'Учеба' },
            { id: 3, groupName: 'Дом' },
        ];

        expect(sortItemsByGroup(items, ['Учеба', 'Дом'], (item) => item.groupName).map((item) => item.id)).toEqual([2, 1, 3]);
    });
});

describe('moveGroup', () => {
    it('swaps neighbors and ignores out-of-bounds moves', () => {
        expect(moveGroup(['Дом', 'Учеба', 'Спорт'], 1, -1)).toEqual(['Учеба', 'Дом', 'Спорт']);
        expect(moveGroup(['Дом', 'Учеба'], 0, -1)).toEqual(['Дом', 'Учеба']);
    });
});

describe('reorderGroupsBySlot', () => {
    it('moves a group to the requested insertion slot', () => {
        expect(reorderGroupsBySlot(['Дом', 'Учеба', 'Спорт'], 0, 3)).toEqual(['Учеба', 'Спорт', 'Дом']);
        expect(reorderGroupsBySlot(['Дом', 'Учеба', 'Спорт'], 2, 0)).toEqual(['Спорт', 'Дом', 'Учеба']);
    });
});

describe('isNoopGroupDrop', () => {
    it('treats before-self and after-self slots as no-op drops', () => {
        expect(isNoopGroupDrop(1, 1)).toBe(true);
        expect(isNoopGroupDrop(1, 2)).toBe(true);
        expect(isNoopGroupDrop(1, 0)).toBe(false);
    });
});

describe('getEffectiveGroupOrder', () => {
    const child = {
        id: 7,
        nickname: 'Маша',
        balance: 10,
        taskGroupOrder: ['Дом', 'Учеба'],
        childTaskGroupOrder: ['Учеба', 'Дом'],
    };

    it('returns the parent order for admins', () => {
        expect(getEffectiveGroupOrder(child, 'tasks', true)).toEqual(['Дом', 'Учеба']);
    });

    it('prefers the child override for child sessions', () => {
        expect(getEffectiveGroupOrder(child, 'tasks', false)).toEqual(['Учеба', 'Дом']);
    });
});

describe('applyGroupOrderToChildren', () => {
    it('updates only the targeted child and order scope', () => {
        const children = [
            { id: 7, nickname: 'Маша', balance: 10, taskGroupOrder: ['Дом'] },
            { id: 8, nickname: 'Петя', balance: 15, taskGroupOrder: ['Спорт'] },
        ];

        const updated = applyGroupOrderToChildren(children, 7, 'tasks', true, ['Учеба', 'Дом']);

        expect(updated[0]?.taskGroupOrder).toEqual(['Учеба', 'Дом']);
        expect(updated[1]?.taskGroupOrder).toEqual(['Спорт']);
    });
});

describe('orderGroupsByFrequency', () => {
    it('orders groups by item count with most frequent first', () => {
        const items = [
            { id: 1, groupName: 'Дом' },
            { id: 2, groupName: 'Учеба' },
            { id: 3, groupName: 'Дом' },
            { id: 4, groupName: 'Спорт' },
            { id: 5, groupName: 'Дом' },
        ];

        expect(
            orderGroupsByFrequency(items, (item) => item.groupName, () => true, null)
        ).toEqual(['Дом', 'Учеба', 'Спорт']);
    });

    it('puts blocked groups (all items inactive) at the bottom', () => {
        const items = [
            { id: 1, groupName: 'Дом', isActive: true },
            { id: 2, groupName: 'Учеба', isActive: false },
            { id: 3, groupName: 'Дом', isActive: true },
            { id: 4, groupName: 'Спорт', isActive: false },
            { id: 5, groupName: 'Спорт', isActive: false },
        ];

        expect(
            orderGroupsByFrequency(
                items,
                (item) => item.groupName,
                (item) => item.isActive !== false,
                null
            )
        ).toEqual(['Дом', 'Учеба', 'Спорт']);
    });

    it('respects preferred order when provided', () => {
        const items = [
            { id: 1, groupName: 'Дом' },
            { id: 2, groupName: 'Учеба' },
            { id: 3, groupName: 'Дом' },
            { id: 4, groupName: 'Спорт' },
            { id: 5, groupName: 'Дом' },
        ];

        expect(
            orderGroupsByFrequency(items, (item) => item.groupName, () => true, ['Спорт', 'Дом'])
        ).toEqual(['Спорт', 'Дом', 'Учеба']);
    });

    it('handles empty items list', () => {
        expect(orderGroupsByFrequency([], () => '', () => true, null)).toEqual([]);
    });

    it('sorts alphabetically within same frequency', () => {
        const items = [
            { id: 1, groupName: 'Бег' },
            { id: 2, groupName: 'Спорт' },
            { id: 3, groupName: 'Дом' },
            { id: 4, groupName: 'Учеба' },
        ];

        expect(
            orderGroupsByFrequency(items, (item) => item.groupName, () => true, null)
        ).toEqual(['Бег', 'Дом', 'Спорт', 'Учеба']);
    });
});