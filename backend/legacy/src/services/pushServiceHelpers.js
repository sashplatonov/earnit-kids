function getPendingRequests(data) {
    return (data?.requests || []).filter((item) => item.status === 'pending');
}

function indexById(items) {
    const map = new Map();
    items.forEach((item) => {
        map.set(String(item.id), item);
    });
    return map;
}

function getNewHistoryEntries(beforeData, afterData) {
    const beforeIds = new Set((beforeData?.history || []).map((entry) => String(entry.id)));
    return (afterData?.history || []).filter((entry) => !beforeIds.has(String(entry.id)));
}

function detectCreatedRequests(beforeData, afterData) {
    const beforeMap = indexById(getPendingRequests(beforeData));
    return getPendingRequests(afterData).filter((req) => !beforeMap.has(String(req.id)));
}

function detectApprovedRequests(beforeData, afterData) {
    const beforePending = getPendingRequests(beforeData);
    const afterPendingMap = indexById(getPendingRequests(afterData));
    const removedPending = beforePending.filter((req) => !afterPendingMap.has(String(req.id)));
    if (removedPending.length === 0) return [];

    const newHistory = getNewHistoryEntries(beforeData, afterData);
    if (newHistory.length === 0) return [];

    return removedPending.filter((req) => {
        return newHistory.some((entry) => {
            const sameChild = String(entry.childId || '') === String(req.childId || '');
            const sameAmount = Number(entry.amount || 0) === Number(req.coins || 0);
            return sameChild && sameAmount;
        });
    });
}

function detectBalanceChanges(beforeChildren, afterChildren) {
    const beforeMap = new Map((beforeChildren || []).map((child) => [String(child.id), child]));
    const changes = [];

    (afterChildren || []).forEach((child) => {
        const prev = beforeMap.get(String(child.id));
        if (!prev) return;

        const previousBalance = Number(prev.balance || 0);
        const currentBalance = Number(child.balance || 0);
        if (previousBalance === currentBalance) return;

        changes.push({
            childId: child.id,
            childName: child.name || 'Ребенок',
            delta: currentBalance - previousBalance,
            balance: currentBalance
        });
    });

    return changes;
}

function dedupeTokens(tokens) {
    const seen = new Set();
    return tokens.filter((item) => {
        const key = item.pushType === 'web' ? item.endpoint : item.token;
        if (!key || seen.has(key)) return false;
        seen.add(key);
        return true;
    });
}

module.exports = {
    detectBalanceChanges,
    detectApprovedRequests,
    detectCreatedRequests,
    dedupeTokens
};
