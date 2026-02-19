import { state, notify, setState } from './state.js';
import { saveDataToServer } from './api.js';
import { renderAll, updateBalanceUI, renderTasks, renderShop, renderHistory, renderRequests } from './ui.js';
import { showToast, showConfirm, showMobileEventNotification } from './utils.js';

// Helper to access global CONFIG
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
            children: state.children // Include children for balance sync
        });
    }, 500);
}

function getActingChildId() {
    if (state.currentChildId) return state.currentChildId;
    if (state.isAdmin) return null;
    if (state.role === 'child' && state.children.length > 0) return state.children[0].id;

    const fromTask = state.tasks.find(t => t.childId)?.childId;
    if (fromTask) return fromTask;
    const fromShop = state.shopItems.find(i => i.childId)?.childId;
    if (fromShop) return fromShop;
    const fromHistory = state.history.find(h => h.childId)?.childId;
    if (fromHistory) return fromHistory;
    const fromRequest = state.requests.find(r => r.childId)?.childId;
    if (fromRequest) return fromRequest;
    return null;
}

function buildTaskHistoryDescription(task) {
    if (!task) return '';

    const parts = [task.name];
    if (task.group) parts.push(`Группа: ${task.group}`);
    if (task.comment) parts.push(`Описание: ${task.comment}`);
    return parts.join(' | ');
}

export function addHistoryEntry(type, amount, description, options = {}) {
    const {
        relatedId = null,
        moneyAmount = 0,
        childIdOverride = null
    } = options;
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

    entry.childId = childIdOverride || getActingChildId();

    state.history.unshift(entry);
    scheduleSave();
    renderHistory();
    updateBalanceUI(); // Update stats potentially
}

export function checkLimits(item, moneyPrice, childIdOverride = null) {
    const now = new Date();
    const currentMonth = now.toISOString().slice(0, 7);
    const actingChildId = childIdOverride || getActingChildId();

    // Calculate stats locally since we need them for check
    let moneySpent = 0;
    let largePurchase = null;

    // 1. Calc monthly stats
    state.history.forEach(entry => {
        if (actingChildId && entry.childId != actingChildId) return;
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
            if (actingChildId && h.childId != actingChildId) return;
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
    const child = state.children.find(c => c.id == childId);
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
        if (h.type === 'earn' && h.date.startsWith(today) && h.childId == childId) {
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

    const actingId = getActingChildId();
    if (!actingId) {
        showToast('Сначала выберите ребенка', 'error');
        return;
    }

    // Always use the maximum configured money limit for shop item.
    const limit = item.moneyLimit || item.money_limit;
    const moneyPrice = (limit && limit > 0) ? limit : 0;

    // Check global limits
    const limitError = checkLimits(item, moneyPrice, actingId);
    if (limitError) {
        showToast(limitError, 'error');
        return;
    }

    if (!state.isAdmin) {
        showConfirm(
            'Отправить заявку на покупку?',
            `"${item.name}" за ${item.price} 🪙`,
            () => {
                state.requests.push({
                    id: Date.now(),
                    childId: actingId,
                    requestType: 'shop_purchase',
                    itemId: item.id,
                    taskId: item.id,
                    taskName: item.name,
                    coins: item.price,
                    moneyAmount: moneyPrice,
                    status: 'pending',
                    date: new Date().toISOString()
                });

                scheduleSave();
                renderRequests();
                const reqBtn = document.querySelector('.nav__btn[data-tab="requests"]');
                if (reqBtn) reqBtn.click();
                showMobileEventNotification('Заявка на покупку отправлена', 'success', 'Новая заявка');
            }
        );
        return;
    }

    showConfirm(
        'Подтвердите покупку',
        `Купить "${item.name}" за ${item.price} 🪙?`,
        () => {
            // Update specific child balance logic needed?
            // "state.balance" is used for display. 
            // If admin, we should update state.children match.
            if (state.isAdmin && state.currentChildId) {
                const child = state.children.find(c => c.id === state.currentChildId);
                if (child) {
                    child.balance -= item.price;
                    state.balance = child.balance; // Sync back for UI
                }
            } else {
                state.balance -= item.price;
            }

            addHistoryEntry('spend', item.price, item.name, { relatedId: item.id, moneyAmount: moneyPrice });
            scheduleSave();
            renderAll();
            showMobileEventNotification(`Вы купили: ${item.name}!`, 'success', 'Баланс изменён');
        }
    );
}

export function earnCoins(taskId) {
    const task = state.tasks.find(t => t.id == taskId);
    if (!task) return;

    const actingId = state.role === 'admin'
        ? state.currentChildId
        : (state.children.length > 0 ? state.children[0].id : null);
    if (!actingId) {
        showToast('Сначала выберите ребенка', 'error');
        return;
    }

    // Check frequency limit
    let frequencyLimitWarning = null;
    if (task.frequency) {
        const { limit, period } = task.frequency;
        let startDate = new Date();

        if (period === 'day') startDate.setHours(0, 0, 0, 0);
        if (period === 'week') startDate.setDate(startDate.getDate() - startDate.getDay() + 1); // Monday
        if (period === 'month') startDate.setDate(1);

        const startTime = startDate.getTime();
        let count = 0;

        state.history.forEach(h => {
            if (h.childId != actingId) return;
            const isMatch = h.taskId == task.id || (h.type === 'earn' && h.description === task.name);
            if (isMatch && new Date(h.date).getTime() >= startTime) {
                count++;
            }
        });

        if (count >= limit) {
            if (period === 'day' || period === 'week') {
                frequencyLimitWarning = `Лимит исчерпан: ${limit} раз(а) в ${period}`;
            } else {
                showToast(`Лимит исчерпан: ${limit} раз(а) в ${period}`, 'error');
                return;
            }
        }
    }

    // Check coin limit
    const limitErr = checkDailyCoinLimit(actingId, task.coins);
    const warnings = [];
    if (frequencyLimitWarning) warnings.push(frequencyLimitWarning);
    if (limitErr) warnings.push(limitErr);

    const historyDescription = buildTaskHistoryDescription(task);

    const applyEarn = () => {
        // Update balance
        if (state.isAdmin && state.currentChildId) {
            const child = state.children.find(c => c.id === state.currentChildId);
            if (child) {
                child.balance += task.coins;
                state.balance = child.balance; // Sync back
            }
        } else {
            state.balance += task.coins;
        }

        addHistoryEntry('earn', task.coins, historyDescription, { relatedId: task.id });
        renderShop(); // Update shop availability
        showMobileEventNotification(`+${task.coins} 🪙 начислено!`, 'success', 'Баланс изменён');
    };

    if (warnings.length > 0) {
        showConfirm(
            'Превышен лимит',
            `${warnings.join('. ')}. Все равно начислить?`,
            applyEarn
        );
        return;
    }

    showConfirm(
        'Выполнить задание?',
        `Подтвердить выполнение задания "${task.name}"?`,
        applyEarn
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
        childId: getActingChildId(),
        requestType: 'earn',
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

    showMobileEventNotification('Заявка отправлена!', 'success', 'Новая заявка');
}

export function deleteHistoryItem(id) {
    if (!confirm('Удалить эту запись из истории?')) return;
    const removedEntry = state.history.find(h => h.id == id);

    if (removedEntry) {
        const delta = removedEntry.type === 'earn'
            ? -(removedEntry.amount || 0)
            : (removedEntry.type === 'spend' ? (removedEntry.amount || 0) : 0);
        const targetChildId = removedEntry.childId || getActingChildId();

        if (targetChildId) {
            const child = state.children.find(c => c.id == targetChildId);
            if (child) {
                child.balance += delta;
                if (!state.isAdmin || state.currentChildId == targetChildId) {
                    state.balance = child.balance;
                }
            } else {
                state.balance += delta;
            }
        } else {
            state.balance += delta;
        }
    }

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

    if (req.requestType === 'shop_purchase') {
        const child = state.children.find(c => c.id == req.childId);
        if (child && child.balance < req.coins) {
            showToast(`Недостаточно монет у ребенка (${child.name})`, 'error');
            return;
        }

        const shopItem = state.shopItems.find(i => i.id == (req.itemId || req.taskId));
        if (shopItem) {
            const limitErr = checkLimits(shopItem, req.moneyAmount || 0, req.childId);
            if (limitErr && !confirm(`${limitErr}. Все равно подтвердить?`)) {
                return;
            }
        }

        if (state.isAdmin && child) {
            child.balance -= req.coins;
            if (req.childId === state.currentChildId) {
                state.balance = child.balance;
            }
        } else {
            state.balance -= req.coins;
        }

        addHistoryEntry('spend', req.coins, req.taskName || 'Покупка', {
            relatedId: req.itemId || req.taskId,
            moneyAmount: req.moneyAmount || 0,
            childIdOverride: req.childId
        });
        state.requests = state.requests.filter(r => r.id != reqId);

        scheduleSave();
        renderAll();
        showMobileEventNotification(`Покупка подтверждена: -${req.coins} 🪙`, 'success', 'Заявка подтверждена');
        return;
    }

    // Check limit
    const limitErr = checkDailyCoinLimit(req.childId, req.coins);
    if (limitErr && !confirm(`${limitErr}. Все равно начислить?`)) {
        return;
    }

    // Find child and update local balance if admin view
    if (state.isAdmin) {
        const child = state.children.find(c => c.id === req.childId);
        if (child) {
            child.balance += req.coins;
            if (req.childId === state.currentChildId) {
                state.balance = child.balance; // Sync if currently viewing
            }
        }
    } else {
        state.balance += req.coins;
    }
    const sourceTask = state.tasks.find(t => t.id == req.taskId && (!req.childId || t.childId == req.childId));
    const historyDescription = buildTaskHistoryDescription(sourceTask) || req.taskName;

    addHistoryEntry('earn', req.coins, historyDescription, {
        relatedId: req.taskId,
        moneyAmount: 0,
        childIdOverride: req.childId
    });

    // Remove request
    state.requests = state.requests.filter(r => r.id != reqId);

    scheduleSave();
    renderAll();
    showMobileEventNotification(`Заявка подтверждена: +${req.coins} 🪙`, 'success', 'Заявка подтверждена');
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

export function adminAwardCoins() {
    if (!state.isAdmin) return;
    const childId = state.currentChildId;
    if (!childId) return showToast('Сначала выберите ребенка', 'error');

    const amountInput = prompt('Введите количество монет для начисления:');
    if (amountInput === null) return;
    const amount = parseInt(amountInput);
    if (isNaN(amount) || amount === 0) return showToast('Некорректная сумма', 'error');

    const description = prompt('Введите описание (причина начисления):', 'Бонус от родителей');
    if (description === null) return;

    // Update balance
    const child = state.children.find(c => c.id === childId);
    if (child) {
        child.balance += amount;
        state.balance = child.balance;
    } else {
        state.balance += amount;
    }

    addHistoryEntry(amount > 0 ? 'earn' : 'spend', Math.abs(amount), description.trim() || 'Начисление вне заданий');
    scheduleSave();
    renderAll();
    showMobileEventNotification(`${amount > 0 ? 'Начислено' : 'Списано'}: ${Math.abs(amount)} 🪙`, 'success', 'Баланс изменён');
}
