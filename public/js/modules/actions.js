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

export function addHistoryEntry(type, amount, description, relatedId = null, moneyAmount = 0) {
    const entry = {
        id: Date.now(),
        type: type, // 'earn' | 'spend'
        amount: amount,
        description: description,
        date: new Date().toISOString()
    };

    if (moneyAmount) entry.moneyAmount = moneyAmount;

    if (relatedId) {
        if (type === 'spend') entry.itemId = relatedId;
        else if (type === 'earn') entry.taskId = relatedId;
        // fallback to just saving it
        entry.relatedId = relatedId;
    }

    if (relatedId) {
        if (type === 'spend') entry.itemId = relatedId;
        else if (type === 'earn') entry.taskId = relatedId;
        // fallback to just saving it
        entry.relatedId = relatedId;
    }

    // Add childId to history entry so strict filtering works
    // If admin, currentChildId. If child, infer?
    // 'state.currentChildId' is what we view.
    // If child is logged in, they are usually viewing themselves.
    // Ideally history entry should have 'childId'.
    if (state.currentChildId) {
        entry.childId = state.currentChildId;
    } else if (state.role === 'child' && state.children.length > 0) {
        entry.childId = state.children[0].id;
    }

    state.history.unshift(entry);
    scheduleSave();
    renderHistory();
    updateBalanceUI(); // Update stats potentially
}

export function checkLimits(item, moneyPrice) {
    const now = new Date();
    const currentMonth = now.toISOString().slice(0, 7);

    // Calculate stats locally since we need them for check
    let moneySpent = 0;
    let largePurchase = null;

    // 1. Calc monthly stats
    state.history.forEach(entry => {
        if (entry.type !== 'spend' || !entry.date.startsWith(currentMonth)) return;
        const amount = entry.moneyAmount || entry.rsdAmount || 0;
        moneySpent += amount;
        if (entry.itemId) {
            const histItem = state.shopItems.find(i => i.id == entry.itemId);
            if (histItem && histItem.type === 'large') largePurchase = histItem.name;
        }
    });

    // 1. Check budget limit
    const monthlyLimit = state.monthlyLimit || CONFIG.MONTHLY_LIMIT;
    if (moneySpent + moneyPrice > monthlyLimit) {
        return `Превышен месячный лимит (осталось ${monthlyLimit - moneySpent})`;
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
            if (h.itemId == item.id && new Date(h.date).getTime() >= startTime) {
                freqCount++;
            }
        });

        if (freqCount >= limit) {
            return `Лимит частоты: ${limit} раз(а) в ${period}`;
        }
    }

    return null; // OK
}

export function checkDailyCoinLimit(childId, amount) {
    // If Admin is acting but on a specific child
    // In multi-child, state.balance is updated. 
    // We need to know who is 'earning'.
    // If state.role == 'child', childId = state.currentChildId (which is likely set or implicit).
    // If state.role == 'admin', childId = state.currentChildId.

    // We need to look at the child's limit.
    const child = state.children.find(c => c.id === childId);
    if (!child) return null; // Can't check limit

    const limit = child.dailyCoinLimit;
    if (!limit || limit <= 0) return null; // No limit

    // Calculate today's earnings
    const today = new Date().toISOString().slice(0, 10); // YYYY-MM-DD
    let earnedToday = 0;

    // Check history (which should be filtered by child if loaded correctly or check all)
    // Note: state.history might contain ALL history if family data loaded.
    // We should filter by childId in history entry.
    state.history.forEach(h => {
        if (h.type === 'earn' && h.date.startsWith(today) && h.childId === childId) {
            earnedToday += h.amount;
        }
    });

    if (earnedToday + amount > limit) {
        return `Превышен дневной лимит монет (${earnedToday}/${limit})`;
    }
    return null;
}

export function buyItem(itemId) {
    const item = state.shopItems.find(i => i.id == itemId);
    if (!item) return;

    if (state.balance < item.price) {
        showToast('Недостаточно монет!', 'error');
        return;
    }

    // Ask for actual money value
    const limit = item.moneyLimit || item.money_limit;
    const limitLabel = limit ? ` (макс ${limit})` : '';
    const moneyInput = prompt(`Покупка "${item.name}"\nВведите стоимость в деньгах${limitLabel}:`, '0');
    if (moneyInput === null) return; // Cancelled

    const moneyPrice = parseInt(moneyInput);
    if (isNaN(moneyPrice) || moneyPrice < 0) {
        showToast('Некорректная сумма', 'error');
        return;
    }

    if (limit && moneyPrice > limit) {
        showToast(`Цена выше лимита товара (${limit})`, 'error');
        return;
    }

    // Check global limits
    const limitError = checkLimits(item, moneyPrice);
    if (limitError) {
        showToast(limitError, 'error');
        return;
    }

    showConfirm(
        'Подтвердите покупку',
        `Купить "${item.name}" за ${item.price} 🪙 и ${moneyPrice} в деньгах?`,
        () => {
            // Update specific child balance logic needed?
            // "state.balance" is used for display. 
            // If admin, we should update state.children match.
            if (state.isAdmin && state.currentChildId) {
                const child = state.children.find(c => c.id === state.currentChildId);
                if (child) child.balance -= item.price;
            } else {
                state.balance -= item.price;
            }

            addHistoryEntry('spend', item.price, item.name, item.id, moneyPrice);
            scheduleSave();
            renderAll();
            showToast(`Вы купили: ${item.name}!`, 'success');
        }
    );
}

export function earnCoins(taskId) {
    const task = state.tasks.find(t => t.id == taskId);
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
            const isMatch = h.taskId == task.id || (h.type === 'earn' && h.description === task.name);
            if (isMatch && new Date(h.date).getTime() >= startTime) {
                count++;
            }
        });

        if (count >= limit) {
            showToast(`Лимит исчерпан: ${limit} раз(а) в ${period}`, 'error');
            return;
        }
    }



    // Check coin limit
    const actingId = state.role === 'admin' ? state.currentChildId : (state.children.length > 0 ? state.children[0].id : null);
    const limitErr = checkDailyCoinLimit(actingId, task.coins);
    if (limitErr) {
        showToast(limitErr, 'error');
        return;
    }

    showConfirm(
        'Начислить монеты?',
        `Начислить ${task.coins} 🪙 за "${task.name}"?`,
        () => {
            // Update balance
            if (state.isAdmin && state.currentChildId) {
                const child = state.children.find(c => c.id === state.currentChildId);
                if (child) child.balance += task.coins;
            } else {
                state.balance += task.coins;
            }

            addHistoryEntry('earn', task.coins, task.name, task.id);
            renderShop(); // Update shop availability
            showToast(`+${task.coins} 🪙 начислено!`, 'success');
        }
    );
}

export function requestCoins(taskId) {
    const task = state.tasks.find(t => t.id == taskId);
    if (!task) {
        console.warn('Task not found for request:', taskId);
        return;
    }

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
    newState.history = newState.history.filter(h => h.id != id);
    setState(newState);

    scheduleSave();
    renderAll();
    showToast('Запись удалена', 'info');
}

export function approveRequest(reqId) {
    const req = state.requests.find(r => r.id == reqId);
    if (!req) return;

    // Check limit
    const limitErr = checkDailyCoinLimit(req.childId, req.coins);
    if (limitErr && !confirm(`${limitErr}. Все равно начислить?`)) {
        return;
    }

    // Find child and update local balance if admin view
    if (state.isAdmin) {
        const child = state.children.find(c => c.id === req.childId);
        if (child) child.balance += req.coins;
    } else {
        state.balance += req.coins;
    }
    addHistoryEntry('earn', req.coins, req.taskName, req.taskId);

    // Remove request
    state.requests = state.requests.filter(r => r.id != reqId);

    scheduleSave();
    renderAll();
    showToast(`Заявка подтверждена: +${req.coins} 🪙`, 'success');
}

export function rejectRequest(reqId) {
    const req = state.requests.find(r => r.id == reqId);
    if (!req) return;

    if (!confirm('Отклонить заявку?')) return;

    // Remove request
    state.requests = state.requests.filter(r => r.id != reqId);

    scheduleSave();
    renderRequests();
    showToast('Заявка отклонена', 'info');
}

export function deleteRequest(reqId) {
    if (!confirm('Удалить заявку?')) return;
    state.requests = state.requests.filter(r => r.id != reqId);
    scheduleSave();
    renderRequests();
    showToast('Заявка удалена', 'info');
}
