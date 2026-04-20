/** Server contract normalization — replaces legacy server-contract.js */

export function getGroupName(entity: Record<string, unknown> | null | undefined): string | null {
    return (entity?.groupName ?? entity?.group ?? entity?.category ?? null) as string | null;
}

export function normalizeChild(child: Record<string, unknown> = {}) {
    const nickname = (child.nickname ?? child.name ?? '') as string;

    return {
        ...child,
        nickname,
        name: (child.name ?? nickname) as string,
        monthlyLimit: child.monthlyLimit ?? child.monthly_limit ?? 10000,
        dailyCoinLimit: child.dailyCoinLimit ?? child.daily_coin_limit ?? 0,
    };
}

export function normalizeTask(task: Record<string, unknown> = {}) {
    const name = (task.name ?? task.title ?? '') as string;
    const coins = (task.coins ?? task.price ?? 0) as number;
    return {
        ...task,
        name,
        title: name,
        coins,
        groupName: task.groupName ?? task.group ?? null,
        comment: task.comment ?? null,
        moneyLimit: task.moneyLimit ?? task.money_limit ?? null,
        ageMin: task.ageMin ?? task.age_min ?? null,
        ageMax: task.ageMax ?? task.age_max ?? null,
    };
}

export function normalizeShopItem(item: Record<string, unknown> = {}) {
    const name = (item.name ?? item.title ?? '') as string;
    const price = (item.price ?? item.coins ?? 0) as number;
    return {
        ...item,
        name,
        title: name,
        price,
        coins: price,
        groupName: item.groupName ?? item.group ?? null,
        comment: item.comment ?? null,
        moneyLimit: item.moneyLimit ?? item.money_limit ?? null,
        ageMin: item.ageMin ?? item.age_min ?? null,
        ageMax: item.ageMax ?? item.age_max ?? null,
    };
}

function normalizeBaseData(baseData: Record<string, unknown> = {}) {
    return {
        tasks: Array.isArray(baseData.tasks) ? baseData.tasks.map(normalizeTask) : [],
        products: Array.isArray(baseData.products) ? baseData.products.map(normalizeShopItem) : [],
    };
}

function getCreatedAt(entity: Record<string, unknown>): string | null {
    return (entity.createdAt ?? entity.created_at ?? entity.timestamp ?? entity.date ?? null) as string | null;
}

export function normalizeHistoryEntry(entry: Record<string, unknown> = {}) {
    const relatedId = entry.relatedId ?? null;
    const taskId = entry.taskId ?? (entry.type === 'earn' || entry.type === 'task_completed' ? relatedId : null);
    const itemId = entry.itemId ?? (entry.type === 'spend' || entry.type === 'purchase' ? relatedId : null);
    return {
        ...entry,
        taskId,
        itemId,
        groupName: entry.groupName ?? entry.group ?? null,
        createdAt: getCreatedAt(entry),
    };
}

export function normalizeRequest(request: Record<string, unknown> = {}) {
    return {
        ...request,
        createdAt: getCreatedAt(request),
        taskGroup: request.taskGroup ?? request.group ?? null,
        itemGroup: request.itemGroup ?? request.group ?? null,
        taskComment: request.taskComment ?? request.comment ?? null,
    };
}

export function normalizeServerData(data: Record<string, unknown> = {}) {
    return {
        ...data,
        tasks: Array.isArray(data.tasks) ? data.tasks.map(normalizeTask) : [],
        shop: Array.isArray(data.shop) ? data.shop.map(normalizeShopItem) : [],
        history: Array.isArray(data.history) ? data.history.map(normalizeHistoryEntry) : [],
        requests: Array.isArray(data.requests) ? data.requests.map(normalizeRequest) : [],
        children: Array.isArray(data.children) ? data.children.map(normalizeChild) : [],
    };
}

function parseBoolean(v: unknown): boolean {
    return v === true || v === 'true' || v === 1 || v === '1';
}

export function buildInitialState(data: Record<string, unknown>, baseData: Record<string, unknown>): Record<string, unknown> {
    const normalized = normalizeServerData(data) as Record<string, unknown>;
    const normalizedBaseData = normalizeBaseData(baseData);
    const isAdmin = parseBoolean(normalized.isAdmin);
    return {
        isAdmin,
        role: isAdmin ? 'admin' : null,
        baseData: normalizedBaseData,
        isLoading: false,
        familyId: normalized.familyId ?? null,
        balance: normalized.balance ?? 0,
        rules: (normalized.rules as string | null | undefined) ?? null,
        tasks: normalized.tasks,
        shopItems: normalized.shop,
        history: normalized.history,
        requests: normalized.requests,
        children: normalized.children,
        childNickname: normalized.childNickname ?? null,
        monthlyLimit: normalized.monthlyLimit ?? 10000,
        dailyCoinLimit: normalized.dailyCoinLimit ?? 0,
    };
}
