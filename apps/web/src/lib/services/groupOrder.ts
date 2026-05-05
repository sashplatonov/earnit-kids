import type { Child } from '$lib/stores/app';

export const UNGROUPED_LABEL = 'Без группы';

export type GroupOrderSection = 'tasks' | 'shop';

export function isNoopGroupDrop(sourceIndex: number, slotIndex: number): boolean {
    return slotIndex === sourceIndex || slotIndex === sourceIndex + 1;
}

function sanitizeGroupOrder(groups: readonly string[] | null | undefined): string[] {
    if (!groups || groups.length === 0) {
        return [];
    }

    const result: string[] = [];
    for (const group of groups) {
        const trimmed = group.trim();
        if (trimmed && !result.includes(trimmed)) {
            result.push(trimmed);
        }
    }

    return result;
}

function resolveField(section: GroupOrderSection, isAdmin: boolean) {
    if (section === 'tasks') {
        return isAdmin ? 'taskGroupOrder' : 'childTaskGroupOrder';
    }

    return isAdmin ? 'shopGroupOrder' : 'childShopGroupOrder';
}

export function normalizeGroupLabel(value: unknown): string {
    if (typeof value !== 'string') {
        return UNGROUPED_LABEL;
    }

    const trimmed = value.trim();
    return trimmed || UNGROUPED_LABEL;
}

export function orderGroups(groups: readonly string[], preferredOrder: readonly string[] | null | undefined): string[] {
    const uniqueGroups = sanitizeGroupOrder(groups);
    if (uniqueGroups.length === 0) {
        return [];
    }

    const preferred = sanitizeGroupOrder(preferredOrder);
    const ordered = preferred.filter((group) => uniqueGroups.includes(group));
    const remainder = uniqueGroups.filter((group) => !ordered.includes(group));

    return [...ordered, ...remainder];
}

export function orderGroupsByFrequency<T>(
    items: readonly T[],
    getGroupLabel: (item: T) => string,
    getItemActive: (item: T) => boolean,
    preferredOrder: readonly string[] | null | undefined
): string[] {
    const groups = new Map<string, { total: number; active: number }>();

    for (const item of items) {
        const label = getGroupLabel(item);
        const isActive = getItemActive(item);
        const entry = groups.get(label);
        if (entry) {
            entry.total++;
            if (isActive) entry.active++;
        } else {
            groups.set(label, { total: 1, active: isActive ? 1 : 0 });
        }
    }

    const groupList = Array.from(groups.entries());

    const hasBlocked = groupList.some(([, stats]) => stats.active === 0 && stats.total > 0);

    if (hasBlocked) {
        groupList.sort(([labelA, statsA], [labelB, statsB]) => {
            const aBlocked = statsA.active === 0;
            const bBlocked = statsB.active === 0;

            if (aBlocked && !bBlocked) return 1;
            if (!aBlocked && bBlocked) return -1;

            if (statsB.total !== statsA.total) {
                return statsB.total - statsA.total;
            }

            return labelA.localeCompare(labelB);
        });
    } else {
        groupList.sort(([labelA, statsA], [labelB, statsB]) => {
            if (statsB.total !== statsA.total) {
                return statsB.total - statsA.total;
            }
            return labelA.localeCompare(labelB);
        });
    }

    const orderedByFrequency = groupList.map(([label]) => label);

    const preferred = sanitizeGroupOrder(preferredOrder);
    const userOrdered = preferred.filter((g) => orderedByFrequency.includes(g));
    const remainder = orderedByFrequency.filter((g) => !userOrdered.includes(g));

    return [...userOrdered, ...remainder];
}

export function sortItemsByGroup<T>(
    items: readonly T[],
    orderedGroups: readonly string[],
    getGroupLabel: (item: T) => string
): T[] {
    const rank = new Map(orderedGroups.map((group, index) => [group, index]));

    return items
        .map((item, index) => ({
            item,
            index,
            rank: rank.get(getGroupLabel(item)) ?? orderedGroups.length,
        }))
        .sort((left, right) => left.rank - right.rank || left.index - right.index)
        .map(({ item }) => item);
}

export function moveGroup(groups: readonly string[], index: number, direction: -1 | 1): string[] {
    const nextIndex = index + direction;
    if (index < 0 || index >= groups.length || nextIndex < 0 || nextIndex >= groups.length) {
        return [...groups];
    }

    const next = [...groups];
    [next[index], next[nextIndex]] = [next[nextIndex], next[index]];
    return next;
}

export function reorderGroupsBySlot(groups: readonly string[], sourceIndex: number, slotIndex: number): string[] {
    if (sourceIndex < 0 || sourceIndex >= groups.length || slotIndex < 0 || slotIndex > groups.length) {
        return [...groups];
    }

    if (isNoopGroupDrop(sourceIndex, slotIndex)) {
        return [...groups];
    }

    const next = [...groups];
    const [movedGroup] = next.splice(sourceIndex, 1);
    const insertionIndex = slotIndex > sourceIndex ? slotIndex - 1 : slotIndex;
    next.splice(insertionIndex, 0, movedGroup);
    return next;
}

export function getEffectiveGroupOrder(
    child: Child | null | undefined,
    section: GroupOrderSection,
    isAdmin: boolean
): string[] {
    const defaultOrder = sanitizeGroupOrder(
        section === 'tasks' ? child?.taskGroupOrder : child?.shopGroupOrder
    );
    if (isAdmin) {
        return defaultOrder;
    }

    const personalOrder = sanitizeGroupOrder(
        section === 'tasks' ? child?.childTaskGroupOrder : child?.childShopGroupOrder
    );
    return personalOrder.length > 0 ? personalOrder : defaultOrder;
}

export function hasSavedGroupOrder(
    child: Child | null | undefined,
    section: GroupOrderSection,
    isAdmin: boolean
): boolean {
    const field = resolveField(section, isAdmin);
    const value = child?.[field];
    return Array.isArray(value) && value.length > 0;
}

export function applyGroupOrderToChildren(
    children: readonly Child[],
    childId: unknown,
    section: GroupOrderSection,
    isAdmin: boolean,
    groups: readonly string[]
): Child[] {
    const field = resolveField(section, isAdmin);
    const nextGroups = sanitizeGroupOrder(groups);

    return children.map((child) =>
        String(child.id) === String(childId)
            ? { ...child, [field]: nextGroups }
            : child
    );
}