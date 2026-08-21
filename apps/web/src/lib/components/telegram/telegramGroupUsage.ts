export type GroupUsageKind = 'tasks' | 'shop' | 'history';

export type GroupUsage = {
    count: number;
    lastAt: number;
};

const STORAGE_KEY_PREFIX = 'earnit:telegram-group-usage';

function storageKey(kind: GroupUsageKind): string {
    return `${STORAGE_KEY_PREFIX}:${kind}`;
}

/**
 * Load per-kind group selection usage from localStorage.
 * This is a lightweight local read model — no domain state is used.
 */
export function loadGroupUsage(kind: GroupUsageKind): Record<string, GroupUsage> {
    try {
        const raw = localStorage.getItem(storageKey(kind));
        if (!raw) return {};
        const parsed: unknown = JSON.parse(raw);
        if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) return {};
        const result: Record<string, GroupUsage> = {};
        for (const [group, value] of Object.entries(parsed as Record<string, unknown>)) {
            const entry = value as Partial<GroupUsage> | null;
            if (entry && typeof entry === 'object' && typeof entry.count === 'number' && typeof entry.lastAt === 'number') {
                result[group] = { count: entry.count, lastAt: entry.lastAt };
            }
        }
        return result;
    } catch {
        return {};
    }
}

/** Record an explicit group filter selection for the given kind. */
export function recordGroupUsage(kind: GroupUsageKind, group: string) {
    if (!group) return;
    try {
        const usage = loadGroupUsage(kind);
        const previous = usage[group];
        usage[group] = {
            count: (previous?.count ?? 0) + 1,
            lastAt: Date.now(),
        };
        localStorage.setItem(storageKey(kind), JSON.stringify(usage));
    } catch {
        // storage unavailable — usage tracking is best-effort only
    }
}

/**
 * Rank groups deterministically: most frequently selected first, then most
 * recently selected, then alphabetically. Groups with no history sort last
 * (by name), so new/unseen groups appear under "Ещё" until they gain usage.
 */
export function rankGroups(groups: readonly string[], usage: Record<string, GroupUsage>): string[] {
    return [...groups].sort((first, second) => {
        const firstUsage = usage[first];
        const secondUsage = usage[second];
        const firstCount = firstUsage?.count ?? 0;
        const secondCount = secondUsage?.count ?? 0;
        if (firstCount !== secondCount) return secondCount - firstCount;
        const firstAt = firstUsage?.lastAt ?? 0;
        const secondAt = secondUsage?.lastAt ?? 0;
        if (firstAt !== secondAt) return secondAt - firstAt;
        return first.localeCompare(second);
    });
}
