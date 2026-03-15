/** @file Action Tasks frontend UI module */
import { state } from './state.js';
import { renderShop, renderRequests } from './ui.js';
import { showToast, showConfirm, showMobileEventNotification } from './utils.js';
import { scheduleSave, addHistoryEntry, checkDailyCoinLimit, getActingChildId, updateBalanceLocally, addRequestEntry, checkFrequency } from './action-helpers.js';
import { triggerTaskAnimation } from './motion-feedback.js';

export function earnCoins(taskId) {
    const task = state.tasks.find(t => t.id == taskId);
    if (!task) return;
    const actingId = state.role === 'admin' ? state.currentChildId : (state.children[0]?.id || null);
    if (!actingId) {
        const msg = (state.isAdmin && state.children.length === 0) ? 'Сначала добавьте ребенка' : 'Сначала выберите ребенка';
        return showToast(msg, 'error');
    }

    const warnings = [checkFrequency(task, actingId), checkDailyCoinLimit(actingId, task.coins)].filter(Boolean);

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
        showMobileEventNotification(`+${task.coins} мон. начислено!`, 'success', 'Balance updated');
        triggerTaskAnimation();
    };

    if (warnings.length > 0) return showConfirm('Превышен лимит', `${warnings.join('. ')}. Все равно начислить?`, { onConfirm: apply });
    const msg = `Подтвердить выполнение задания "${escapeHtml(task.name)}" за ${task.coins} <span class="gamified-icon icon-coin-stack" aria-hidden="true" style="width: 1.2rem; height: 1.2rem; vertical-align: middle;"></span>?`;
    showConfirm('Выполнить задание?', msg, { onConfirm: apply });
}

export function requestCoins(taskId) {
    const task = state.tasks.find(t => t.id == taskId);
    if (!task) return;
    const actingId = getActingChildId();
    if (!actingId) return showToast('Сначала выберите ребенка', 'error');

    const warnings = [checkFrequency(task, actingId), checkDailyCoinLimit(actingId, task.coins)].filter(Boolean);

    if (warnings.length > 0) {
        return showConfirm('Лимит исчерпан', warnings.join('. '), { cancelLabel: 'Понятно', hideConfirm: true });
    }

    const apply = () => {
        addRequestEntry({
            childId: actingId,
            requestType: 'earn',
            taskId: task.id,
            taskName: task.name,
            coins: task.coins
        });
        scheduleSave();
        renderRequests();
        document.querySelector('.nav__btn[data-tab="requests"]')?.click();
        showMobileEventNotification('Заявка отправлена!', 'success', 'Новая заявка');
        triggerTaskAnimation();
    };

    const msg = `"${escapeHtml(task.name)}" за ${task.coins} <span class="gamified-icon icon-coin-stack" aria-hidden="true" style="width: 1.2rem; height: 1.2rem; vertical-align: middle;"></span>`;
    showConfirm('Отправить заявку?', msg, { onConfirm: apply });
}
