import { state } from './state.js';
import { saveDataToServer } from './api.js';
import { renderHistory, updateBalanceUI } from './ui.js';

const CONFIG = window.CONFIG;
let saveTimeout = null;

export function scheduleSave() {
    if (saveTimeout) clearTimeout(saveTimeout);
    saveTimeout = setTimeout(async () => {
        await saveDataToServer({
            balance: state.balance,
            tasks: state.tasks,
            shop: state.shopItems,
            history: state.history,
            requests: state.requests,
            children: state.children
        });
    }, 500);
}

export function getActingChildId() {
    if (state.currentChildId) return state.currentChildId;
    if (state.isAdmin) return null;
    if (state.role === 'child' && state.children.length > 0) return state.children[0].id;

    const findId = (arr, key) => arr.find(x => x[key])?.[key];
    return findId(state.tasks, 'childId') || findId(state.shopItems, 'childId') ||
        findId(state.history, 'childId') || findId(state.requests, 'childId') || null;
}

export function addHistoryEntry({ type, amount, description, relatedId, moneyAmount, childIdOverride }) {
    const entry = {
        id: Date.now(),
        type,
        amount,
        description,
        date: new Date().toISOString(),
        moneyAmount: moneyAmount || undefined,
        childId: childIdOverride || getActingChildId()
    };

    if (relatedId) {
        if (type === 'spend') entry.itemId = relatedId;
        else if (type === 'earn') entry.taskId = relatedId;
        entry.relatedId = relatedId;
    }

    state.history.unshift(entry);
    scheduleSave();
    renderHistory();
    updateBalanceUI();
}

function getMonthlyStats(actingChildId, currentMonth) {
    let moneySpent = 0;
    let largePurchase = null;
    state.history.forEach(entry => {
        if ((actingChildId && entry.childId != actingChildId) || entry.type !== 'spend' || !entry.date.startsWith(currentMonth)) return;
        moneySpent += (entry.moneyAmount || entry.rsdAmount || 0);
        if (entry.itemId) {
            const histItem = state.shopItems.find(i => i.id == entry.itemId);
            if (histItem?.type === 'large') largePurchase = histItem.name;
        }
    });
    return { moneySpent, largePurchase };
}

function checkFreq(item, actingChildId) {
    if (!item.frequency) return null;
    const { limit, period } = item.frequency;
    let start = new Date();
    if (period === 'day') start.setHours(0, 0, 0, 0);
    else if (period === 'week') start.setDate(start.getDate() - start.getDay() + 1);
    else if (period === 'month') start.setDate(1);

    const count = state.history.filter(h => (actingChildId ? h.childId == actingChildId : true) && h.itemId == item.id && new Date(h.date) >= start).length;
    return count >= limit ? `Лимит частоты: ${limit} раз(а) в ${period}` : null;
}

export function checkLimits(item, moneyPrice, childIdOverride = null) {
    const actingChildId = childIdOverride || getActingChildId();
    const stats = getMonthlyStats(actingChildId, new Date().toISOString().slice(0, 7));

    const monthlyLimit = state.monthlyLimit || (CONFIG?.MONTHLY_LIMIT || 10000);
    if (stats.moneySpent + moneyPrice > monthlyLimit) return `Превышен месячный лимит (осталось ${monthlyLimit - stats.moneySpent})`;
    if (item.type === 'large' && stats.largePurchase) return `Уже была крупная покупка в этом месяце (${stats.largePurchase})`;

    return checkFreq(item, actingChildId);
}

export function checkDailyCoinLimit(childId, amount) {
    const child = state.children.find(c => c.id == childId);
    if (!child || !child.dailyCoinLimit || child.dailyCoinLimit <= 0) return null;

    const today = new Date().toISOString().slice(0, 10);
    const earnedToday = state.history.reduce((sum, h) =>
        (h.type === 'earn' && h.date.startsWith(today) && h.childId == childId) ? sum + h.amount : sum, 0);

    return earnedToday + amount > child.dailyCoinLimit ? `Превышен дневной лимит монет (${earnedToday}/${child.dailyCoinLimit})` : null;
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
