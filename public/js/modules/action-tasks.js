/** @file Action Tasks frontend UI module */
import { state } from './state.js';
import { renderShop, renderRequests } from './ui.js';
import { showToast, showConfirm, showMobileEventNotification } from './utils.js';
import { scheduleSave, addHistoryEntry, checkDailyCoinLimit, getActingChildId, updateBalanceLocally } from './action-helpers.js';

function checkTaskFrequency(task, childId) {
    if (!task.frequency) return null;
    const { limit, period } = task.frequency;
    let start = new Date();
    if (period === 'day') start.setHours(0, 0, 0, 0);
    else if (period === 'week') start.setDate(start.getDate() - start.getDay() + 1);
    else if (period === 'month') start.setDate(1);

    const count = state.history.filter(h => h.childId == childId && (h.taskId == task.id || (h.type === 'earn' && h.description === task.name)) && new Date(h.date) >= start).length;
    return count >= limit ? `Лимит исчерпан: ${limit} раз(а) в ${period}` : null;
}

export function earnCoins(taskId) {
    const task = state.tasks.find(t => t.id == taskId);
    if (!task) return;
    const actingId = state.role === 'admin' ? state.currentChildId : (state.children[0]?.id || null);
    if (!actingId) return showToast('Сначала выберите ребенка', 'error');

    const warnings = [checkTaskFrequency(task, actingId), checkDailyCoinLimit(actingId, task.coins)].filter(Boolean);

    const apply = () => {
        updateBalanceLocally(actingId, task.coins);
        addHistoryEntry({
            type: 'earn',
            amount: task.coins,
            description: task.name,
            group: task.group,
            comment: task.comment,
            relatedId: task.id,
            childIdOverride: actingId
        });
        renderShop();
        showMobileEventNotification(`+${task.coins} 🪙 начислено!`, 'success', 'Balance updated');
    };

    if (warnings.length > 0) return showConfirm('Превышен лимит', `${warnings.join('. ')}. Все равно начислить?`, apply);
    showConfirm('Выполнить задание?', `Подтвердить выполнение задания "${task.name}"?`, apply);
}

export function requestCoins(taskId) {
    const task = state.tasks.find(t => t.id == taskId);
    if (!task) return;
    state.requests.push({
        id: Date.now(), childId: getActingChildId(), requestType: 'earn',
        taskId: task.id, taskName: task.name, coins: task.coins,
        date: new Date().toISOString(), status: 'pending'
    });
    scheduleSave();
    renderRequests();
    document.querySelector('.nav__btn[data-tab="requests"]')?.click();
    showMobileEventNotification('Заявка отправлена!', 'success', 'Новая заявка');
}
