import { describe, expect, it } from 'vitest';
import {
    applyGroupOrderToChildren,
    getEffectiveGroupOrder,
    moveGroup,
    normalizeGroupLabel,
    orderGroups,
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