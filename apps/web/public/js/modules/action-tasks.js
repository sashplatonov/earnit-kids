/** @file Action Tasks frontend UI module */
import { state } from './state.js';
import { renderAll } from './ui.js';
import { completeTaskOnServer, requestTaskCompletionOnServer } from './api.js';
import { showToast, showConfirm, showMobileEventNotification, escapeHtml } from './utils.js';
import { applyServerFamilyData, checkDailyCoinLimit, checkFrequency, flushPendingSave, getActingChildId } from './action-helpers.js';
import { triggerTaskAnimation } from './motion-feedback.js';

async function commitTaskCompletion(task, actingId) {
    await flushPendingSave();
    const result = await completeTaskOnServer(task.id, actingId);
    if (!result.success || !result.data) {
        showToast(result.error || 'Не удалось начислить монеты', 'error');
        return;
    }

    applyServerFamilyData(result.data, { currentChildId: actingId });
    renderAll();
    showMobileEventNotification(`+${task.coins} мон. начислено!`, 'success', 'Balance updated');
    triggerTaskAnimation();
}

async function commitTaskRequest(task, actingId) {
    await flushPendingSave();
    const result = await requestTaskCompletionOnServer(task.id);
    if (!result.success || !result.data) {
        showToast(result.error || 'Не удалось отправить заявку', 'error');
        return;
    }

    applyServerFamilyData(result.data, { currentChildId: actingId });
    renderAll();
    document.querySelector('.nav__btn[data-tab="requests"]')?.click();
    showMobileEventNotification('Заявка отправлена!', 'success', 'Новая заявка');
    triggerTaskAnimation();
}

export function earnCoins(taskId) {
    const task = state.tasks.find(t => t.id == taskId);
    if (!task) return;
    const actingId = state.isAdmin ? state.currentChildId : (state.children[0]?.id || null);
    if (!actingId) {
        const msg = (state.isAdmin && state.children.length === 0) ? 'Сначала добавьте ребенка' : 'Сначала выберите ребенка';
        return showToast(msg, 'error');
    }

    const warnings = [checkFrequency(task, actingId), checkDailyCoinLimit(actingId, task.coins)].filter(Boolean);

    const apply = () => { void commitTaskCompletion(task, actingId); };

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

    const apply = () => { void commitTaskRequest(task, actingId); };

    const msg = `"${escapeHtml(task.name)}" за ${task.coins} <span class="gamified-icon icon-coin-stack" aria-hidden="true" style="width: 1.2rem; height: 1.2rem; vertical-align: middle;"></span>`;
    showConfirm('Отправить заявку?', msg, { onConfirm: apply });
}
