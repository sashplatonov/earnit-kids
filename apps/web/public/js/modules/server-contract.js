export function getGroupName(entity) {
    return entity?.groupName ?? entity?.group ?? entity?.category ?? null;
}

export function getMoneyLimit(entity) {
    return entity?.moneyLimit ?? entity?.money_limit ?? null;
}

export function getCreatedAt(entity) {
    return entity?.createdAt ?? entity?.created_at ?? entity?.timestamp ?? entity?.date ?? null;
}

function findById(entries, id) {
    if (!Array.isArray(entries) || id === null || id === undefined) return null;
    return entries.find(entry => String(entry.id) === String(id)) || null;
}

function isPurchaseRequest(request) {
    return request?.requestType === 'shop_purchase';
}

function getRequestItem(request, state) {
    return findById(state?.shopItems, request?.itemId ?? request?.taskId);
}

function getRequestTask(request, state) {
    return findById(state?.tasks, request?.taskId);
}

export function getRequestGroup(request, state) {
    const directGroup = request?.itemGroup ?? request?.taskGroup ?? request?.group ?? null;
    if (directGroup) return directGroup;

    const relatedEntity = isPurchaseRequest(request)
        ? getRequestItem(request, state)
        : getRequestTask(request, state);

    return getGroupName(relatedEntity);
}

export function getRequestComment(request, state) {
    const directComment = request?.taskComment ?? request?.comment ?? null;
    if (directComment) return directComment;

    const relatedEntity = isPurchaseRequest(request)
        ? getRequestItem(request, state)
        : getRequestTask(request, state);

    return relatedEntity?.comment ?? null;
}

export function normalizeChild(child = {}) {
    return {
        ...child,
        monthlyLimit: child.monthlyLimit ?? child.monthly_limit ?? 10000,
        dailyCoinLimit: child.dailyCoinLimit ?? child.daily_coin_limit ?? 0
    };
}

export function normalizeTask(task = {}) {
    return {
        ...task,
        groupName: task.groupName ?? task.group ?? null,
        moneyLimit: task.moneyLimit ?? task.money_limit ?? null
    };
}

export function normalizeShopItem(item = {}) {
    return {
        ...item,
        groupName: item.groupName ?? item.group ?? null,
        comment: item.comment ?? null,
        moneyLimit: item.moneyLimit ?? item.money_limit ?? null
    };
}

export function normalizeHistoryEntry(entry = {}) {
    const relatedId = entry.relatedId ?? null;
    const taskId = entry.taskId ?? (entry.type === 'earn' ? relatedId : null);
    const itemId = entry.itemId ?? (entry.type === 'spend' ? relatedId : null);
    return {
        ...entry,
        taskId,
        itemId,
        groupName: entry.groupName ?? entry.group ?? null,
        createdAt: getCreatedAt(entry)
    };
}

export function normalizeRequest(request = {}) {
    return {
        ...request,
        createdAt: getCreatedAt(request),
        taskGroup: request.taskGroup ?? request.group ?? null,
        itemGroup: request.itemGroup ?? request.group ?? null,
        taskComment: request.taskComment ?? request.comment ?? null
    };
}

export function normalizeServerData(data = {}) {
    return {
        ...data,
        tasks: Array.isArray(data.tasks) ? data.tasks.map(normalizeTask) : [],
        shop: Array.isArray(data.shop) ? data.shop.map(normalizeShopItem) : [],
        history: Array.isArray(data.history) ? data.history.map(normalizeHistoryEntry) : [],
        requests: Array.isArray(data.requests) ? data.requests.map(normalizeRequest) : [],
        children: Array.isArray(data.children) ? data.children.map(normalizeChild) : []
    };
}