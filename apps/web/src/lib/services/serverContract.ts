/** Server contract normalization — replaces legacy server-contract.js */

import type { AuthResponseSnapshot, FamilyChoice, MembershipPermission } from '$lib/types/auth';

export function getGroupName(entity: Record<string, unknown> | null | undefined): string | null {
    return (entity?.groupName ?? entity?.group ?? entity?.category ?? null) as string | null;
}

function readStringArray(value: unknown): string[] {
    if (!Array.isArray(value)) {
        return [];
    }

    const result: string[] = [];
    for (const entry of value) {
        if (typeof entry !== 'string') {
            continue;
        }

        const trimmed = entry.trim();
        if (trimmed && !result.includes(trimmed)) {
            result.push(trimmed);
        }
    }

    return result;
}

export function normalizeChild(child: Record<string, unknown> = {}) {
    const nickname = (child.nickname ?? child.name ?? '') as string;

    return {
        ...child,
        nickname,
        name: (child.name ?? nickname) as string,
        monthlyLimit: child.monthlyLimit ?? child.monthly_limit ?? 10000,
        dailyCoinLimit: child.dailyCoinLimit ?? child.daily_coin_limit ?? 0,
        theme: (child.theme ?? null) as string | null,
        taskGroupOrder: readStringArray(child.taskGroupOrder ?? child.task_group_order),
        shopGroupOrder: readStringArray(child.shopGroupOrder ?? child.shop_group_order),
        childTaskGroupOrder: readStringArray(child.childTaskGroupOrder ?? child.child_task_group_order),
        childShopGroupOrder: readStringArray(child.childShopGroupOrder ?? child.child_shop_group_order),
    };
}

export function normalizeTask(task: Record<string, unknown> = {}) {
    const name = (task.name ?? task.title ?? '') as string;
    const coins = (task.coins ?? task.price ?? 0) as number;
    const isActive = task.isActive ?? task.is_active;
    return {
        ...task,
        name,
        title: name,
        coins,
        isActive: isActive == null ? true : parseBoolean(isActive),
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
    const isActive = item.isActive ?? item.is_active;
    return {
        ...item,
        name,
        title: name,
        price,
        coins: price,
        isActive: isActive == null ? true : parseBoolean(isActive),
        groupName: item.groupName ?? item.group ?? null,
        comment: item.comment ?? null,
        moneyLimit: item.moneyLimit ?? item.money_limit ?? null,
        frequency: item.frequency ?? null,
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
    const title = (entry.title ?? entry.taskName ?? entry.itemName ?? entry.description ?? null) as string | null;
    return {
        ...entry,
        title,
        taskId,
        itemId,
        groupName: entry.groupName ?? entry.group ?? null,
        comment: entry.comment ?? null,
        createdAt: getCreatedAt(entry),
    };
}

export function normalizeRequest(request: Record<string, unknown> = {}) {
    const taskComment = request.taskComment ?? request.description ?? request.comment ?? null;
    const itemComment = request.itemComment ?? request.description ?? request.comment ?? null;
    const groupName = request.groupName ?? request.taskGroup ?? request.itemGroup ?? request.group ?? null;
    return {
        ...request,
        title: request.title ?? request.itemName ?? request.taskName ?? null,
        description: request.description ?? taskComment ?? itemComment ?? null,
        comment: request.comment ?? request.description ?? null,
        note: request.note ?? null,
        createdAt: getCreatedAt(request),
        groupName,
        taskGroup: request.taskGroup ?? groupName,
        itemGroup: request.itemGroup ?? groupName,
        taskComment,
        itemComment,
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

function normalizePermission(value: unknown): MembershipPermission | null {
    return value === 'viewer' || value === 'editor' || value === 'family_admin' ? value : null;
}

function normalizeFamilyChoice(choice: Record<string, unknown> = {}): FamilyChoice {
    return {
        familyId: (choice.familyId ?? choice.family_id ?? '') as string,
        familyName: (choice.familyName ?? choice.family_name ?? '') as string,
        permission: normalizePermission(choice.permission) ?? 'viewer',
        blocked: parseBoolean(choice.blocked),
    };
}

export function normalizeAuthResponse(data: Record<string, unknown> = {}): AuthResponseSnapshot {
    return {
        success: parseBoolean(data.success),
        role: (data.role ?? null) as string | null,
        familyId: (data.familyId ?? data.family_id ?? null) as string | null,
        childId: (data.childId ?? data.child_id ?? null) as number | null,
        childName: (data.childName ?? data.child_name ?? null) as string | null,
        error: (data.error ?? null) as string | null,
        selectionRequired: parseBoolean(data.selectionRequired),
        familyChoices: Array.isArray(data.familyChoices)
            ? data.familyChoices.map(choice => normalizeFamilyChoice(choice as Record<string, unknown>))
            : null,
    };
}

export function buildInitialState(data: Record<string, unknown>, baseData: Record<string, unknown>): Record<string, unknown> {
    const normalized = normalizeServerData(data) as Record<string, unknown>;
    const normalizedBaseData = normalizeBaseData(baseData);
    const role = typeof normalized.role === 'string' ? normalized.role : null;
    const isAdmin = parseBoolean(normalized.isAdmin) || role === 'admin' || role === 'parent' || role === 'super_admin';
    return {
        isAdmin,
        role: role ?? (isAdmin ? 'admin' : null),
        permission: normalizePermission(normalized.permission),
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
