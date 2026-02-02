import { state, notify, setState } from './state.js';
import { saveDataToServer } from './api.js';
import { renderAll, updateBalanceUI, renderTasks, renderShop, renderHistory, renderRequests } from './ui.js';
import { showToast, showConfirm } from './utils.js';

// Helper to access global CONFIG
const CONFIG = window.CONFIG;

let saveTimeout = null;
export function scheduleSave() {
    if (saveTimeout) clearTimeout(saveTimeout);
    saveTimeout = setTimeout(async () => {
        await saveDataToServer({
            // pin: state.adminPin, // Logic from original app.js: only send if set?
            // Actually, server handles PIN preservation. We just send data.
            balance: state.balance,
            tasks: state.tasks,
            shop: state.shopItems,
            history: state.history,
            requests: state.requests
        });
    }, 500);
}

export function addHistoryEntry(type, amount, description, relatedId = null, rsdAmount = 0) {
    const entry = {
        id: Date.now(),
        type: type, // 'earn' | 'spend'
        amount: amount,
        description: description,
        date: new Date().toISOString()
    };

    if (rsdAmount) entry.rsdAmount = rsdAmount;

    if (relatedId) {
        if (type === 'spend') entry.itemId = relatedId;
        else if (type === 'earn') entry.taskId = relatedId;
        // fallback to just saving it
        entry.relatedId = relatedId;
    }

    state.history.unshift(entry);
    scheduleSave();
    renderHistory();
    updateBalanceUI(); // Update stats potentially
}

export function checkLimits(item, rsdPrice) {
    const now = new Date();
    const currentMonth = now.toISOString().slice(0, 7);

    // Calculate stats locally since we need them for check
    let rsdSpent = 0;
    let largePurchase = null;
    let count = 0;

    // 1. Calc monthly stats
    state.history.forEach(entry => {
        if (entry.type !== 'spend' || !entry.date.startsWith(currentMonth)) return;
        if (entry.rsdAmount) rsdSpent += entry.rsdAmount;
        if (entry.itemId) {
            const histItem = state.shopItems.find(i => i.id === entry.itemId);
            if (histItem && histItem.type === 'large') largePurchase = histItem.name;
        }
    });

    // 1. Check budget limit
    if (rsdSpent + rsdPrice > CONFIG.MONTHLY_LIMIT) {
        return `Превышен месячный лимит (осталось ${CONFIG.MONTHLY_LIMIT - rsdSpent} ${CONFIG.RSD_SYMBOL})`;
    }

    // 2. Check large purchase limit
    if (item.type === 'large' && largePurchase) {
        return `Уже была крупная покупка в этом месяце (${largePurchase})`;
    }

    // 3. Check frequency
    if (item.frequency) {
        const { limit, period } = item.frequency;
        let startDate = new Date();

        if (period === 'day') startDate.setHours(0, 0, 0, 0);
        if (period === 'week') startDate.setDate(startDate.getDate() - startDate.getDay() + 1); // Monday
        if (period === 'month') startDate.setDate(1);

        const startTime = startDate.getTime();
        let freqCount = 0;

        state.history.forEach(h => {
            if (h.itemId === item.id && new Date(h.date).getTime() >= startTime) {
                freqCount++;
            }
        });

        if (freqCount >= limit) {
            return `Лимит частоты: ${limit} раз(а) в ${period}`;
        }
    }

    return null; // OK
}

export function buyItem(itemId) {
    const item = state.shopItems.find(i => i.id === itemId);
    if (!item) return;

    if (state.balance < item.price) {
        showToast('Недостаточно монет!', 'error');
        return;
    }

    // Ask for actual RSD price
    const limit = item.moneyLimit || item.money_limit || item.rsdLimit;
    const limitLabel = limit ? ` (макс ${limit})` : '';
    const rsdInput = prompt(`Покупка "${item.name}"\nВведите стоимость в деньгах ${limitLabel}:`, '0');
    if (rsdInput === null) return; // Cancelled

    const rsdPrice = parseInt(rsdInput);
    if (isNaN(rsdPrice) || rsdPrice < 0) {
        showToast('Некорректная сумма', 'error');
        return;
    }

    if (limit && rsdPrice > limit) {
        showToast(`Цена выше лимита товара (${limit})`, 'error');
        return;
    }

    // Check global limits
    const limitError = checkLimits(item, rsdPrice);
    if (limitError) {
        showToast(limitError, 'error');
        return;
    }

    showConfirm(
        'Подтвердите покупку',
        `Купить "${item.name}" за ${item.price} 🪙 и ${rsdPrice} в деньгах?`,
        () => {
            state.balance -= item.price;
            addHistoryEntry('spend', item.price, item.name, item.id, rsdPrice);
            scheduleSave();
            renderAll();
            showToast(`Вы купили: ${item.name}!`, 'success');
        }
    );
}

export function earnCoins(taskId) {
    const task = state.tasks.find(t => t.id === taskId);
    if (!task) return;

    // Check frequency limit
    if (task.frequency) {
        const { limit, period } = task.frequency;
        let startDate = new Date();

        if (period === 'day') startDate.setHours(0, 0, 0, 0);
        if (period === 'week') startDate.setDate(startDate.getDate() - startDate.getDay() + 1); // Monday
        if (period === 'month') startDate.setDate(1);

        const startTime = startDate.getTime();
        let count = 0;

        state.history.forEach(h => {
            const isMatch = h.taskId === task.id || (h.type === 'earn' && h.description === task.name);
            if (isMatch && new Date(h.date).getTime() >= startTime) {
                count++;
            }
        });

        if (count >= limit) {
            showToast(`Лимит исчерпан: ${limit} раз(а) в ${period}`, 'error');
            return;
        }
    }

    showConfirm(
        'Начислить монеты?',
        `Начислить ${task.coins} 🪙 за "${task.name}"?`,
        () => {
            state.balance += task.coins;
            addHistoryEntry('earn', task.coins, task.name, task.id);
            renderShop(); // Update shop availability
            showToast(`+${task.coins} 🪙 начислено!`, 'success');
        }
    );
}

export function requestCoins(taskId) {
    const task = state.tasks.find(t => t.id === taskId);
    if (!task) return;

    const request = {
        id: Date.now(),
        taskId: task.id,
        taskName: task.name,
        coins: task.coins,
        date: new Date().toISOString(),
        status: 'pending'
    };

    state.requests.push(request);
    scheduleSave();
    renderRequests();

    // Switch to requests tab
    const reqBtn = document.querySelector('.nav__btn[data-tab="requests"]');
    if (reqBtn) reqBtn.click();

    showToast('Заявка отправлена!', 'success');
}

export function deleteHistoryItem(id) {
    if (!confirm('Удалить эту запись из истории?')) return;
    const newState = { ...state };
    newState.history = newState.history.filter(h => h.id !== id);
    setState(newState);

    scheduleSave();
    renderAll();
    showToast('Запись удалена', 'info');
}

export function approveRequest(reqId) {
    const req = state.requests.find(r => r.id === reqId);
    if (!req) return;

    state.balance += req.coins;
    addHistoryEntry('earn', req.coins, req.taskName, req.taskId);

    // Remove request
    state.requests = state.requests.filter(r => r.id !== reqId);

    scheduleSave();
    renderAll();
    showToast(`Заявка подтверждена: +${req.coins} 🪙`, 'success');
}

export function rejectRequest(reqId) {
    const req = state.requests.find(r => r.id === reqId);
    if (!req) return;

    if (!confirm('Отклонить заявку?')) return;

    // Remove request
    state.requests = state.requests.filter(r => r.id !== reqId);

    scheduleSave();
    renderRequests();
    showToast('Заявка отклонена', 'info');
}

export function deleteRequest(reqId) {
    if (!confirm('Удалить заявку?')) return;
    state.requests = state.requests.filter(r => r.id !== reqId);
    scheduleSave();
    renderRequests();
    showToast('Заявка удалена', 'info');
}
