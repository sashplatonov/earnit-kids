const DEFAULT_CHILD_KEY = '__default__';

function buildLegacyFamilySnapshot(familyId, familyRecord = {}, familyData = {}) {
    const mergedData = mergeSources(familyRecord, familyData);
    const hasScopedData = hasScopedArray(familyRecord.tasks, familyData.tasks)
        || hasScopedArray(familyRecord.shop, familyData.shop)
        || hasScopedArray(familyRecord.history, familyData.history)
        || hasScopedArray(familyRecord.requests, familyData.requests);
    const children = normalizeChildren(mergedData);
    const defaultChildKey = children[0]?.legacyKey || DEFAULT_CHILD_KEY;
    const preferredChildKey = resolvePreferredChildKey(children, mergedData, defaultChildKey);
    const resolveChildKey = createChildKeyResolver(children, preferredChildKey, defaultChildKey);

    return {
        familyId,
        children,
        preferredChildKey,
        tasks: normalizeTasks(mergedData.tasks, resolveChildKey),
        shopItems: normalizeShopItems(mergedData.shop, resolveChildKey),
        historyEntries: normalizeHistory(mergedData.history, resolveChildKey),
        requests: normalizeRequests(mergedData.requests, resolveChildKey),
        friendFamilyIds: normalizeFriendFamilyIds(mergedData.friends),
        hasScopedData
    };
}

function hasScopedArray(primary, fallback) {
    return Array.isArray(primary) || Array.isArray(fallback);
}

function mergeSources(familyRecord, familyData) {
    return {
        ...familyRecord,
        ...familyData,
        children: pickArray(familyData.children, familyRecord.children),
        tasks: pickArray(familyData.tasks, familyRecord.tasks),
        shop: pickArray(familyData.shop, familyRecord.shop),
        history: pickArray(familyData.history, familyRecord.history),
        requests: pickArray(familyData.requests, familyRecord.requests),
        friends: pickArray(familyData.friends, familyRecord.friends)
    };
}

function pickArray(primary, fallback) {
    if (Array.isArray(primary)) {
        return primary;
    }
    if (Array.isArray(fallback)) {
        return fallback;
    }
    return [];
}

function normalizeChildren(source) {
    const rawChildren = Array.isArray(source.children) ? source.children : [];
    if (rawChildren.length === 0) {
        return [buildDefaultChild(source)];
    }

    return rawChildren.map((child, index) => {
        const legacyId = readLegacyId(child);
        return {
            legacyKey: legacyId !== null ? `legacy:${legacyId}` : `index:${index}`,
            legacyId,
            name: normalizeString(child.name) || normalizeString(child.nickname) || `Child ${index + 1}`,
            token: normalizeString(child.token),
            balance: normalizeInteger(child.balance, rawChildren.length === 1 ? normalizeInteger(source.balance, 0) : 0),
            monthlyLimit: normalizeInteger(child.monthlyLimit ?? child.monthly_limit, normalizeInteger(source.monthlyLimit ?? source.monthly_limit, 10000)),
            dailyCoinLimit: normalizeInteger(child.dailyCoinLimit ?? child.daily_coin_limit, normalizeInteger(source.dailyCoinLimit ?? source.daily_coin_limit, 0)),
            theme: normalizeString(child.theme) || normalizeString(source.theme) || 'ocean',
            createdAt: normalizeDate(child.createdAt ?? child.created_at ?? source.createdAt ?? source.created_at)
        };
    });
}

function buildDefaultChild(source) {
    return {
        legacyKey: DEFAULT_CHILD_KEY,
        legacyId: null,
        name: normalizeString(source.childNickname ?? source.child_nickname) || 'Child',
        token: normalizeString(source.child_token ?? source.childToken),
        balance: normalizeInteger(source.balance, 0),
        monthlyLimit: normalizeInteger(source.monthlyLimit ?? source.monthly_limit, 10000),
        dailyCoinLimit: normalizeInteger(source.dailyCoinLimit ?? source.daily_coin_limit, 0),
        theme: normalizeString(source.theme) || 'ocean',
        createdAt: normalizeDate(source.createdAt ?? source.created_at)
    };
}

function readLegacyId(entity) {
    const candidate = entity?.id ?? entity?.childId ?? entity?.child_id;
    if (candidate === null || candidate === undefined || candidate === '') {
        return null;
    }
    return String(candidate);
}

function resolvePreferredChildKey(children, source, fallbackKey) {
    const preferredLegacyId = source.lastSelectedChildId ?? source.last_selected_child_id ?? source.currentChildId;
    if (preferredLegacyId !== null && preferredLegacyId !== undefined) {
        const match = children.find((child) => child.legacyId === String(preferredLegacyId));
        if (match) {
            return match.legacyKey;
        }
    }

    const preferredName = normalizeString(source.childNickname ?? source.child_nickname);
    if (preferredName) {
        const match = children.find((child) => child.name.toLowerCase() === preferredName.toLowerCase());
        if (match) {
            return match.legacyKey;
        }
    }

    return fallbackKey;
}

function createChildKeyResolver(children, preferredChildKey, defaultChildKey) {
    const childByLegacyId = new Map(
        children
            .filter((child) => child.legacyId !== null)
            .map((child) => [child.legacyId, child.legacyKey])
    );

    return (entry = {}) => {
        const childId = entry.childId ?? entry.child_id ?? entry.ownerChildId ?? entry.owner_child_id;
        if (childId !== null && childId !== undefined && childByLegacyId.has(String(childId))) {
            return childByLegacyId.get(String(childId));
        }
        return preferredChildKey || defaultChildKey;
    };
}

function normalizeTasks(tasks, resolveChildKey) {
    return normalizeCollection(tasks, (task) => {
        const taskId = normalizeOptionalInteger(task.id ?? task.taskId ?? task.task_id);
        const name = normalizeString(task.name ?? task.title);
        if (taskId === null || !name) {
            return null;
        }

        return {
            childKey: resolveChildKey(task),
            taskId,
            name,
            coins: normalizeInteger(task.coins, 0),
            groupName: normalizeString(task.groupName ?? task.group),
            frequency: normalizeFrequency(task.frequency),
            comment: normalizeString(task.comment),
            moneyLimit: normalizeOptionalInteger(task.moneyLimit ?? task.money_limit),
            deleted: normalizeBoolean(task.isDeleted ?? task.is_deleted)
        };
    });
}

function normalizeShopItems(items, resolveChildKey) {
    return normalizeCollection(items, (item) => {
        const itemId = normalizeOptionalInteger(item.id ?? item.itemId ?? item.item_id);
        const name = normalizeString(item.name ?? item.title);
        if (itemId === null || !name) {
            return null;
        }

        return {
            childKey: resolveChildKey(item),
            itemId,
            name,
            price: normalizeInteger(item.price, 0),
            groupName: normalizeString(item.groupName ?? item.group),
            frequency: normalizeFrequency(item.frequency),
            comment: normalizeString(item.comment),
            moneyLimit: normalizeOptionalInteger(item.moneyLimit ?? item.money_limit),
            deleted: normalizeBoolean(item.isDeleted ?? item.is_deleted)
        };
    });
}

function normalizeHistory(entries, resolveChildKey) {
    return normalizeCollection(entries, (entry) => ({
        childKey: resolveChildKey(entry),
        externalId: normalizeOptionalInteger(entry.id ?? entry.externalId ?? entry.external_id),
        type: normalizeString(entry.type) || 'unknown',
        amount: normalizeInteger(entry.amount, 0),
        description: normalizeString(entry.description),
        moneyAmount: normalizeInteger(entry.moneyAmount ?? entry.money_amount ?? entry.rsdAmount, 0),
        relatedId: normalizeOptionalInteger(entry.relatedId ?? entry.related_id ?? entry.itemId ?? entry.item_id ?? entry.taskId ?? entry.task_id),
        groupName: normalizeString(entry.groupName ?? entry.group),
        comment: normalizeString(entry.comment),
        createdAt: normalizeDate(entry.createdAt ?? entry.created_at ?? entry.timestamp ?? entry.date)
    }));
}

function normalizeRequests(requests, resolveChildKey) {
    return normalizeCollection(requests, (request) => {
        const taskId = normalizeOptionalInteger(request.taskId ?? request.task_id);
        const itemId = normalizeOptionalInteger(request.itemId ?? request.item_id)
            ?? ((request.requestType === 'shop_purchase' || request.request_type === 'shop_purchase') ? taskId : null);

        return {
            childKey: resolveChildKey(request),
            externalId: normalizeOptionalInteger(request.id ?? request.externalId ?? request.external_id),
            taskId,
            taskName: normalizeString(request.taskName ?? request.task_name ?? request.name),
            itemId,
            coins: normalizeInteger(request.coins, 0),
            status: normalizeString(request.status) || 'pending',
            requestType: normalizeString(request.requestType ?? request.request_type) || (itemId !== null ? 'shop_purchase' : 'earn'),
            moneyAmount: normalizeInteger(request.moneyAmount ?? request.money_amount, 0),
            createdAt: normalizeDate(request.createdAt ?? request.created_at ?? request.date)
        };
    });
}

function normalizeFriendFamilyIds(friends) {
    return normalizeCollection(friends, (friend) => {
        const familyId = typeof friend === 'string' || typeof friend === 'number'
            ? String(friend)
            : normalizeString(friend.familyId ?? friend.family_id ?? friend.id);
        return familyId || null;
    });
}

function normalizeCollection(entries, mapper) {
    if (!Array.isArray(entries)) {
        return [];
    }

    return entries
        .map((entry) => mapper(entry || {}))
        .filter((entry) => entry !== null);
}

function normalizeFrequency(value) {
    if (!value) {
        return null;
    }
    if (typeof value === 'string') {
        return value;
    }
    try {
        return JSON.stringify(value);
    } catch {
        return null;
    }
}

function normalizeString(value) {
    if (typeof value !== 'string') {
        return null;
    }
    const trimmed = value.trim();
    return trimmed ? trimmed : null;
}

function normalizeInteger(value, fallback) {
    const numeric = Number(value);
    return Number.isFinite(numeric) ? Math.trunc(numeric) : fallback;
}

function normalizeOptionalInteger(value) {
    if (value === null || value === undefined || value === '') {
        return null;
    }
    return normalizeInteger(value, null);
}

function normalizeBoolean(value) {
    return value === true || value === 'true' || value === 1 || value === '1';
}

function normalizeDate(value) {
    if (!value) {
        return null;
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

module.exports = {
    DEFAULT_CHILD_KEY,
    buildLegacyFamilySnapshot
};
