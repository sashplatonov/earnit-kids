/** @file Action Helpers frontend UI module */
import { state } from './state.js';
import { saveDataToServer } from './api.js';
import { renderHistory, updateBalanceUI } from './ui.js';
import { getCreatedAt, normalizeRequest } from './server-contract.js';

const CONFIG = window.CONFIG;
const REQUEST_HISTORY_LIMIT = 60;
let saveTimeout = null;

export function scheduleSave() {
    if (saveTimeout) clearTimeout(saveTimeout);
    saveTimeout = setTimeout(async () => {
        await saveDataToServer({
            childId: state.currentChildId,
            balance: state.balance,
            tasks: state.tasks,
            shop: state.shopItems,
            history: state.history,
            requests: state.requests,
            children: state.children
        });
    }, 500);
}

export function addRequestEntry(entry) {
    const normalized = normalizeRequest({
        ...entry,
        id: entry.id || Date.now(),
        status: entry.status || 'pending',
        createdAt: entry.createdAt || new Date().toISOString()
    });
    state.requests = [normalized, ...state.requests.filter(r => r.id != normalized.id)];
    if (state.requests.length > REQUEST_HISTORY_LIMIT) {
        state.requests = state.requests.slice(0, REQUEST_HISTORY_LIMIT);
    }
}

export function getActingChildId() {
    if (state.currentChildId) return state.currentChildId;
    if (state.isAdmin) return null;
    if (state.role === 'child' && state.children.length > 0) return state.children[0].id;

    const findId = (arr, key) => arr.find(x => x[key])?.[key];
    return findId(state.tasks, 'childId') || findId(state.shopItems, 'childId') ||
        findId(state.history, 'childId') || findId(state.requests, 'childId') || null;
}

function setEntryReferences(entry, type, relatedId) {
    if (!relatedId) return;
    if (type === 'spend') entry.itemId = relatedId;
    else if (type === 'earn') entry.taskId = relatedId;
    entry.relatedId = relatedId;
}

function buildEntryObject(params) {
    const { type, amount, description, relatedId, moneyAmount, childIdOverride, actingChildId, groupName, comment } = params;
    const entry = {
        id: Date.now(),
        type, amount,
        description: description || undefined,
        groupName: groupName || undefined,
        comment: comment || undefined,
        createdAt: new Date().toISOString(),
        moneyAmount: moneyAmount || undefined,
        childId: childIdOverride || actingChildId
    };
    setEntryReferences(entry, type, relatedId);
    return entry;
}

export function addHistoryEntry(params) {
    const entry = buildEntryObject({ ...params, actingChildId: getActingChildId() });
    state.history.unshift(entry);
    scheduleSave();
    renderHistory();
    updateBalanceUI();
}

function isMatchingSpendEntry(entry, actingChildId, currentMonth) {
    const createdAt = getCreatedAt(entry);
    if (entry.type !== 'spend' || !createdAt?.startsWith(currentMonth)) return false;
    if (!actingChildId) return true;
    return entry.childId == actingChildId;
}

function isMatchingPendingPurchase(req, actingChildId, excludeRequestId) {
    if (req.status !== 'pending' || req.id == excludeRequestId || req.requestType !== 'shop_purchase') {
        return false;
    }
    if (!actingChildId) return true;
    return req.childId == actingChildId;
}

function getMonthlyStats(actingChildId, currentMonth, excludeRequestId = null) {
    let moneySpent = 0;
    let largePurchase = null;
    
    // Check history
    state.history.forEach(entry => {
        if (!isMatchingSpendEntry(entry, actingChildId, currentMonth)) return;
        moneySpent += (entry.moneyAmount || entry.rsdAmount || 0);
        if (entry.itemId) {
            const histItem = state.shopItems.find(i => i.id == entry.itemId);
            if (histItem?.type === 'large') largePurchase = histItem.name;
        }
    });

    // Check pending requests
    state.requests.forEach(req => {
        if (!isMatchingPendingPurchase(req, actingChildId, excludeRequestId)) return;
        moneySpent += (req.moneyAmount || 0);
        const item = state.shopItems.find(i => i.id == (req.itemId || req.taskId));
        if (item?.type === 'large') largePurchase = item.name;
    });

    return { moneySpent, largePurchase };
}

const PERIOD_NAMES = {
    day: 'день',
    week: 'неделю',
    month: 'месяц',
    year: 'год'
};

function getTimeUntilReset(period) {
    const now = new Date();
    const next = new Date();
    if (period === 'day') {
        next.setDate(now.getDate() + 1);
        next.setHours(0, 0, 0, 0);
    } else if (period === 'week') {
        next.setDate(now.getDate() + (7 - (now.getDay() || 7) + 1));
        next.setHours(0, 0, 0, 0);
    } else if (period === 'month') {
        next.setMonth(now.getMonth() + 1, 1);
        next.setHours(0, 0, 0, 0);
    } else {
        return '';
    }

    const diffMs = next - now;
    const diffHours = Math.ceil(diffMs / (1000 * 60 * 60));
    if (diffHours >= 24) {
        const days = Math.floor(diffHours / 24);
        return days === 1 ? '1 день' : `${days} дн.`;
    }
    return `${diffHours} час.`;
}

export function checkFrequency(itemOrTask, childId, excludeRequestId = null) {
    if (!itemOrTask.frequency) return null;
    const { limit, period } = itemOrTask.frequency;
    const periodName = PERIOD_NAMES[period] || period;
    let start = new Date();
    if (period === 'day') start.setHours(0, 0, 0, 0);
    else if (period === 'week') start.setDate(start.getDate() - (start.getDay() || 7) + 1);
    else if (period === 'month') start.setDate(1);

    const actingChildId = childId || getActingChildId();

    const histCount = state.history.filter(h => 
        (actingChildId ? h.childId == actingChildId : true) && 
        (h.itemId == itemOrTask.id || h.taskId == itemOrTask.id || (h.type === 'earn' && h.description === itemOrTask.name)) && 
        new Date(getCreatedAt(h)) >= start
    ).length;

    const reqCount = state.requests.filter(r => 
        r.status === 'pending' && 
        r.id != excludeRequestId &&
        (actingChildId ? r.childId == actingChildId : true) && 
        (r.itemId == itemOrTask.id || r.taskId == itemOrTask.id || r.taskName === itemOrTask.name) && 
        new Date(getCreatedAt(r)) >= start
    ).length;

    const totalCount = histCount + reqCount;
    if (totalCount >= limit) {
        const wait = getTimeUntilReset(period);
        return `Лимит (${limit} раз в ${periodName}) исчерпан. Нужно подождать ${wait}`;
    }
    return null;
}

export function checkLimits(item, moneyPrice, options = {}) {
    const { childIdOverride = null, excludeRequestId = null } = options;
    const actingChildId = childIdOverride || getActingChildId();
    const stats = getMonthlyStats(actingChildId, new Date().toISOString().slice(0, 7), excludeRequestId);

    const monthlyLimit = state.monthlyLimit || (CONFIG?.MONTHLY_LIMIT || 10000);
    const monthlyLimitError = getMonthlyLimitError(stats, moneyPrice, monthlyLimit);
    if (monthlyLimitError) return monthlyLimitError;

    const largePurchaseError = getLargePurchaseError(item, stats);
    if (largePurchaseError) return largePurchaseError;

    return checkFrequency(item, actingChildId, excludeRequestId);
}

function getMonthlyLimitError(stats, moneyPrice, monthlyLimit) {
    if (stats.moneySpent + moneyPrice <= monthlyLimit) return null;

    const wait = getTimeUntilReset('month');
    return `Месячный лимит (${monthlyLimit}) исчерпан. Нужно подождать ${wait}`;
}

function getLargePurchaseError(item, stats) {
    if (item.type !== 'large' || !stats.largePurchase) return null;

    const wait = getTimeUntilReset('month');
    return `Лимит на крупные покупки исчерпан. Нужно подождать ${wait}`;
}

export function checkDailyCoinLimit(childId, amount, excludeRequestId = null) {
    const child = state.children.find(c => c.id == childId);
    if (!child || !child.dailyCoinLimit || child.dailyCoinLimit <= 0) return null;

    const start = new Date();
    start.setHours(0, 0, 0, 0);

    const earnedToday = state.history.reduce((sum, h) =>
        (h.type === 'earn' && h.childId == childId && new Date(getCreatedAt(h)) >= start) ? sum + h.amount : sum, 0);

    const pendingEarn = state.requests.reduce((sum, r) =>
        (r.status === 'pending' && r.id != excludeRequestId && r.requestType === 'earn' && r.childId == childId && new Date(getCreatedAt(r)) >= start) ? sum + (r.coins || 0) : sum, 0);

    const totalEarn = earnedToday + pendingEarn;
    if (totalEarn + amount > child.dailyCoinLimit) {
        const wait = getTimeUntilReset('day');
        return `Дневной лимит (${child.dailyCoinLimit} монет) исчерпан. Нужно подождать ${wait}`;
    }
    return null;
}

export function updateBalanceLocally(childId, delta) {
    if (state.isAdmin && childId) {
        const child = state.children.find(c => c.id == childId);
        if (child) {
            child.balance += delta;
            if (childId == state.currentChildId) state.balance = child.balance;
        }
    } else {
        state.balance += delta;
    }
}
