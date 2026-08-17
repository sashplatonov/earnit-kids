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
        nickname,
        name: (child.name ?? nickname) as string,
        monthlyLimit: child.monthlyLimit ?? child.monthly_limit ?? 10000,
        dailyCoinLimit: child.dailyCoinLimit ?? child.daily_coin_limit ?? 0,
        dailyRewardLimit: child.dailyRewardLimit ?? child.daily_reward_limit ?? 0,
        theme: (child.theme ?? null) as string | null,
        taskGroupOrder: readStringArray(child.taskGroupOrder ?? child.task_group_order),
        shopGroupOrder: readStringArray(child.shopGroupOrder ?? child.shop_group_order),
        childTaskGroupOrder: readStringArray(child.childTaskGroupOrder ?? child.child_task_group_order),
        childShopGroupOrder: readStringArray(child.childShopGroupOrder ?? child.child_shop_group_order),
        hiddenTaskGroupOrder: readStringArray(child.hiddenTaskGroups ?? child.hidden_task_groups ?? child.hiddenTaskGroupOrder),
        hiddenShopGroupOrder: readStringArray(child.hiddenShopGroups ?? child.hidden_shop_groups ?? child.hiddenShopGroupOrder),
        id: (child.id ?? child.childId ?? null) as unknown,
        balance: (child.balance ?? 0) as number,
        rewardGoalItemId: (child.rewardGoalItemId ?? child.reward_goal_item_id ?? null) as number | string | null,
        status: (child.status ?? null) as string | null,
        isPinSet: (child.isPinSet ?? child.is_pin_set ?? false) as boolean,
        ageMin: (child.ageMin ?? child.age_min ?? null) as number | null,
        ageMax: (child.ageMax ?? child.age_max ?? null) as number | null,
    };
}

export function normalizeTask(task: Record<string, unknown> = {}) {
    const name = (task.name ?? task.title ?? '') as string;
    const coins = (task.coins ?? task.price ?? 0) as number;
    const isActive = task.isActive ?? task.is_active;
    return {
        id: (task.id ?? task.taskId ?? null) as unknown,
        name,
        title: name,
        coins,
        isActive: isActive == null ? true : parseBoolean(isActive),
        groupName: task.groupName ?? task.group ?? null,
        icon: task.icon ?? task.graphic ?? null,
        comment: task.comment ?? null,
        cueWhen: task.cueWhen ?? task.cue_when ?? null,
        cueAction: task.cueAction ?? task.cue_action ?? null,
        moneyLimit: task.moneyLimit ?? task.money_limit ?? null,
        ageMin: task.ageMin ?? task.age_min ?? null,
        ageMax: task.ageMax ?? task.age_max ?? null,
        lastCompletedAt: task.lastCompletedAt ?? task.last_completed_at ?? null,
        childId: (task.childId ?? null) as unknown,
        frequency: (task.frequency ?? null) as unknown,
        periodProgress: normalizeTaskPeriodProgress(task.periodProgress ?? task.period_progress),
        sourceCatalogItemId: (task.sourceCatalogItemId ?? task.source_catalog_item_id ?? null) as unknown,
    };
}

function normalizeTaskPeriodProgress(value: unknown) {
    if (!value || typeof value !== 'object') {
        return null;
    }
    const progress = value as Record<string, unknown>;
    const period = typeof progress.period === 'string' ? progress.period : '';
    const windowStart = typeof progress.windowStart === 'string'
        ? progress.windowStart
        : typeof progress.window_start === 'string' ? progress.window_start : '';
    const resetAt = typeof progress.resetAt === 'string'
        ? progress.resetAt
        : typeof progress.reset_at === 'string' ? progress.reset_at : '';
    if (!period || !windowStart || !resetAt) {
        return null;
    }
    const limit = Number(progress.limit ?? 0);
    const completed = Number(progress.completed ?? 0);
    const pending = Number(progress.pending ?? 0);
    const remaining = Number(progress.remaining ?? Math.max(0, limit - completed - pending));
    return {
        period,
        completed: Number.isFinite(completed) ? Math.max(0, completed) : 0,
        pending: Number.isFinite(pending) ? Math.max(0, pending) : 0,
        limit: Number.isFinite(limit) ? Math.max(0, limit) : 0,
        remaining: Number.isFinite(remaining) ? Math.max(0, remaining) : 0,
        available: progress.available !== false,
        windowStart,
        resetAt,
    };
}

export function normalizeShopItem(item: Record<string, unknown> = {}) {
    const name = (item.name ?? item.title ?? '') as string;
    const price = (item.price ?? item.coins ?? 0) as number;
    const isActive = item.isActive ?? item.is_active;
    return {
        id: (item.id ?? item.itemId ?? null) as unknown,
        name,
        title: name,
        price,
        coins: price,
        isActive: isActive == null ? true : parseBoolean(isActive),
        groupName: item.groupName ?? item.group ?? null,
        icon: item.icon ?? item.graphic ?? null,
        comment: item.comment ?? null,
        moneyLimit: item.moneyLimit ?? item.money_limit ?? null,
        frequency: item.frequency ?? null,
        ageMin: item.ageMin ?? item.age_min ?? null,
        ageMax: item.ageMax ?? item.age_max ?? null,
        lastPurchasedAt: item.lastPurchasedAt ?? item.last_purchased_at ?? null,
        childId: (item.childId ?? null) as unknown,
        sourceCatalogItemId: (item.sourceCatalogItemId ?? item.source_catalog_item_id ?? null) as unknown,
        periodProgress: normalizeTaskPeriodProgress(item.periodProgress ?? item.period_progress),
    };
}

function normalizeBaseData(baseData: Record<string, unknown> = {}) {
    return {
        tasks: Array.isArray(baseData.tasks) ? baseData.tasks.map(normalizeTask) : [],
        products: Array.isArray(baseData.products) ? baseData.products.map(normalizeShopItem) : [],
    };
}

function normalizeCatalogTemplate(item: Record<string, unknown>): Record<string, unknown> {
    return {
        id: item.id ?? null,
        title: item.title ?? item.name ?? '',
        comment: item.comment ?? null,
        coins: item.coins ?? null,
        price: item.price ?? null,
        groupKey: item.groupKey ?? item.group_key ?? null,
        groupName: item.groupName ?? item.group_name ?? item.group ?? null,
        semanticGraphicKey: item.semanticGraphicKey ?? item.semantic_graphic_key ?? null,
        frequencyLimit: item.frequencyLimit ?? item.frequency_limit ?? null,
        frequencyPeriod: item.frequencyPeriod ?? item.frequency_period ?? null,
        minAge: item.minAge ?? item.min_age ?? null,
        maxAge: item.maxAge ?? item.max_age ?? null,
        difficulty: item.difficulty ?? null,
        tags: Array.isArray(item.tags) ? item.tags : [],
        active: item.active !== false,
        sortOrder: item.sortOrder ?? item.sort_order ?? 0,
    };
}

function normalizeCatalog(catalog: Record<string, unknown> = {}) {
    return {
        tasks: Array.isArray(catalog.tasks) ? catalog.tasks.map(normalizeCatalogTemplate) : [],
        rewards: Array.isArray(catalog.rewards) ? catalog.rewards.map(normalizeCatalogTemplate) : [],
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
        id: (entry.id ?? null) as unknown,
        type: (entry.type ?? null) as unknown,
        amount: (entry.amount ?? 0) as number,
        description: (entry.description ?? null) as string | null,
        title,
        taskId,
        itemId,
        groupName: entry.groupName ?? entry.group ?? null,
        comment: entry.comment ?? null,
        moneyAmount: (entry.moneyAmount ?? null) as number | null,
        taskName: (entry.taskName ?? null) as string | null,
        itemName: (entry.itemName ?? null) as string | null,
        relatedId,
        createdAt: getCreatedAt(entry),
        childId: (entry.childId ?? null) as unknown,
    };
}

export function normalizeRequest(request: Record<string, unknown> = {}) {
    const taskComment = request.taskComment ?? request.description ?? request.comment ?? null;
    const itemComment = request.itemComment ?? request.description ?? request.comment ?? null;
    const groupName = request.groupName ?? request.taskGroup ?? request.itemGroup ?? request.group ?? null;
    return {
        id: (request.id ?? null) as unknown,
        requestType: (request.requestType ?? request.request_type ?? '') as string,
        taskId: (request.taskId ?? null) as unknown,
        itemId: (request.itemId ?? null) as unknown,
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
        taskName: (request.taskName ?? null) as string | null,
        itemName: (request.itemName ?? null) as string | null,
        coins: (request.coins ?? null) as number | null,
        moneyAmount: (request.moneyAmount ?? null) as number | null,
        status: (request.status ?? 'pending') as string,
        amount: (request.amount ?? null) as number | null,
        childId: (request.childId ?? null) as unknown,
        childNickname: (request.childNickname ?? null) as string | null,
    };
}

export function normalizeServerData(data: Record<string, unknown> = {}) {
    return {
        tasks: Array.isArray(data.tasks) ? data.tasks.map(normalizeTask) : [],
        shop: Array.isArray(data.shop) ? data.shop.map(normalizeShopItem) : [],
        history: Array.isArray(data.history) ? data.history.map(normalizeHistoryEntry) : [],
        requests: Array.isArray(data.requests) ? data.requests.map(normalizeRequest) : [],
        children: Array.isArray(data.children) ? data.children.map(normalizeChild) : [],
        balance: (data.balance ?? 0) as number,
        rules: (data.rules ?? null) as string | null,
        isAdmin: parseBoolean(data.isAdmin),
        role: (data.role ?? null) as string | null,
        permission: data.permission ?? null,
        familyId: (data.familyId ?? data.family_id ?? null) as string | null,
        activeChildId: (data.activeChildId ?? data.active_child_id ?? data.childId ?? null) as unknown,
        childNickname: (data.childNickname ?? null) as string | null,
        monthlyLimit: (data.monthlyLimit ?? 10000) as number,
        dailyCoinLimit: (data.dailyCoinLimit ?? 0) as number,
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
    const normalizedCatalog = normalizeCatalog(baseData.catalog as Record<string, unknown> | undefined);
    const role = typeof normalized.role === 'string' ? normalized.role : null;
    const isAdmin = parseBoolean(normalized.isAdmin) || role === 'admin' || role === 'parent' || role === 'super_admin';
    return {
        isAdmin,
        role: role ?? (isAdmin ? 'admin' : null),
        permission: normalizePermission(normalized.permission),
        baseData: normalizedBaseData,
        catalog: normalizedCatalog,
        isLoading: false,
        familyId: normalized.familyId ?? null,
        currentChildId: normalized.activeChildId ?? null,
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
