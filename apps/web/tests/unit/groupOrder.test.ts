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

describe('task sorting within selected group', () => {
    it('sorts active tasks by frequency limit (descending)', () => {
        const tasks = [
            { id: 1, groupName: 'Дом', frequency: { limit: 3 }, isActive: true },
            { id: 2, groupName: 'Дом', frequency: { limit: 1 }, isActive: true },
            { id: 3, groupName: 'Дом', frequency: { limit: 5 }, isActive: true },
        ];

        const sorted = tasks
            .filter((task) => task.groupName === 'Дом')
            .sort((a, b) => {
                const aActive = a.isActive !== false;
                const bActive = b.isActive !== false;
                if (aActive && !bActive) return -1;
                if (!aActive && bActive) return 1;
                if (!aActive && !bActive) return 0;
                const aLimit = a.frequency?.limit ?? 0;
                const bLimit = b.frequency?.limit ?? 0;
                if (bLimit !== aLimit) return bLimit - aLimit;
                return 0;
            });

        expect(sorted.map((t) => t.id)).toEqual([3, 1, 2]);
    });

    it('puts blocked tasks at the bottom', () => {
        const tasks = [
            { id: 1, groupName: 'Дом', frequency: { limit: 3 }, isActive: false },
            { id: 2, groupName: 'Дом', frequency: { limit: 1 }, isActive: true },
            { id: 3, groupName: 'Дом', frequency: { limit: 5 }, isActive: true },
            { id: 4, groupName: 'Дом', frequency: { limit: 2 }, isActive: false },
        ];

        const sorted = tasks
            .filter((task) => task.groupName === 'Дом')
            .sort((a, b) => {
                const aActive = a.isActive !== false;
                const bActive = b.isActive !== false;
                if (aActive && !bActive) return -1;
                if (!aActive && bActive) return 1;
                if (!aActive && !bActive) return 0;
                const aLimit = a.frequency?.limit ?? 0;
                const bLimit = b.frequency?.limit ?? 0;
                if (bLimit !== aLimit) return bLimit - aLimit;
                return 0;
            });

        expect(sorted.map((t) => t.id)).toEqual([3, 2, 1, 4]);
    });

    it('handles tasks without frequency limit', () => {
        const tasks = [
            { id: 1, groupName: 'Дом', frequency: null, isActive: true },
            { id: 2, groupName: 'Дом', frequency: { limit: 3 }, isActive: true },
            { id: 3, groupName: 'Дом', frequency: undefined, isActive: true },
        ];

        const sorted = tasks
            .filter((task) => task.groupName === 'Дом')
            .sort((a, b) => {
                const aActive = a.isActive !== false;
                const bActive = b.isActive !== false;
                if (aActive && !bActive) return -1;
                if (!aActive && bActive) return 1;
                if (!aActive && !bActive) return 0;
                const aLimit = a.frequency?.limit ?? 0;
                const bLimit = b.frequency?.limit ?? 0;
                if (bLimit !== aLimit) return bLimit - aLimit;
                return 0;
            });

        expect(sorted.map((t) => t.id)).toEqual([2, 1, 3]);
    });
});