/** @file Super admin families filters and sorting helpers */

export function normalizeText(value) {
    return String(value || '').toLowerCase();
}

export function getFamilyChildrenCount(family) {
    if (Number.isFinite(family.childrenCount)) return family.childrenCount;
    if (Array.isArray(family.children)) return family.children.length;
    return 0;
}

export function familyMatchesSearch(family, search) {
    if (!search) return true;
    const query = normalizeText(search);
    const haystack = [
        family.id,
        family.email,
        family.familyId
    ].map(normalizeText).join(' ');
    return haystack.includes(query);
}

export function applyFamiliesFilters(families, viewState) {
    const filtered = families.filter((family) => {
        const statusOk = viewState.status === 'all'
            || (viewState.status === 'active' && !family.isBlocked)
            || (viewState.status === 'blocked' && family.isBlocked);
        return statusOk && familyMatchesSearch(family, viewState.search);
    });

    return filtered.sort((a, b) => {
        if (viewState.sort === 'active') {
            const aTime = a.last_activity ? new Date(a.last_activity).getTime() : 0;
            const bTime = b.last_activity ? new Date(b.last_activity).getTime() : 0;
            return bTime - aTime;
        }
        const aTime = a.created_at ? new Date(a.created_at).getTime() : 0;
        const bTime = b.created_at ? new Date(b.created_at).getTime() : 0;
        return bTime - aTime;
    });
}
