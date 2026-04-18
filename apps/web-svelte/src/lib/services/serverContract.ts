/** Server contract normalization — replaces legacy server-contract.js */

export function getGroupName(entity: Record<string, unknown> | null | undefined): string | null {
    return (entity?.groupName ?? entity?.group ?? entity?.category ?? null) as string | null;
}

export function normalizeChild(child: Record<string, unknown> = {}) {
    return {
        ...child,
        monthlyLimit: child.monthlyLimit ?? child.monthly_limit ?? 10000,
        dailyCoinLimit: child.dailyCoinLimit ?? child.daily_coin_limit ?? 0,
    };
}

export function normalizeTask(task: Record<string, unknown> = {}) {
    return {
        ...task,
        groupName: task.groupName ?? task.group ?? null,
        moneyLimit: task.moneyLimit ?? task.money_limit ?? null,
    };
}

export function normalizeShopItem(item: Record<string, unknown> = {}) {
    return {
        ...item,
        groupName: item.groupName ?? item.group ?? null,
        comment: item.comment ?? null,
        moneyLimit: item.moneyLimit ?? item.money_limit ?? null,
    };
}

function getCreatedAt(entity: Record<string, unknown>): string | null {
    return (entity.createdAt ?? entity.created_at ?? entity.timestamp ?? entity.date ?? null) as string | null;
}

export function normalizeHistoryEntry(entry: Record<string, unknown> = {}) {
    const relatedId = entry.relatedId ?? null;
    const taskId = entry.taskId ?? (entry.type === 'earn' ? relatedId : null);
    const itemId = entry.itemId ?? (entry.type === 'spend' ? relatedId : null);
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
    const isAdmin = parseBoolean(normalized.isAdmin);
    return {
        isAdmin,
        role: isAdmin ? 'admin' : null,
        baseData,
        isLoading: false,
        familyId: normalized.familyId ?? null,
        balance: normalized.balance ?? 0,
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
