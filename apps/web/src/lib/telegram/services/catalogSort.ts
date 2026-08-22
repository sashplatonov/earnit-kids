import { sortItemsByGroup } from './groupOrder';

export type CatalogSortMode = 'group' | 'coins';

export function sortCatalogItems<T>(items: readonly T[], mode: CatalogSortMode, orderedGroups: readonly string[], getGroup: (item: T) => string, getCoins: (item: T) => number): T[] {
    if (mode === 'coins') {
        return items.map((item, index) => ({ item, index })).sort((left, right) => getCoins(left.item) - getCoins(right.item) || left.index - right.index).map(({ item }) => item);
    }
    const grouped = sortItemsByGroup(items, orderedGroups, getGroup);
    const result: T[] = [];
    for (const group of orderedGroups) {
        result.push(...grouped.filter((item) => getGroup(item) === group).sort((left, right) => getCoins(left) - getCoins(right)));
    }
    result.push(...grouped.filter((item) => !orderedGroups.includes(getGroup(item))).sort((left, right) => getCoins(left) - getCoins(right)));
    return result;
}
