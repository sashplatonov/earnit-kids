/** @file Admin Settings frontend UI module */
import { state, setState } from './state.js';
import { updateChildSettings, getChildLink, regenerateChildToken } from './api.js';
import { renderAll } from './ui.js';
import { showToast, showConfirm } from './utils.js';

function getTargetChildId() {
    return state.currentChildId || state.children[0]?.id || null;
}

function applyPayloadToState(payload, childId) {
    const child = state.children.find(c => c.id == childId);
    if (child) {
        if (payload.name) child.name = payload.name;
        if (payload.monthly_limit !== undefined) child.monthlyLimit = payload.monthly_limit;
        if (payload.daily_coin_limit !== undefined) child.dailyCoinLimit = payload.daily_coin_limit;
    }
    const stateUpdates = {};
    if (payload.monthly_limit !== undefined) stateUpdates.monthlyLimit = payload.monthly_limit;
    if (payload.daily_coin_limit !== undefined) stateUpdates.dailyCoinLimit = payload.daily_coin_limit;
    if (Object.keys(stateUpdates).length) setState(stateUpdates);
}

async function persistChildSettings(payload) {
    const childId = getTargetChildId();
    if (!childId) return showToast('Сначала добавьте ребенка', 'error');
    const res = await updateChildSettings(state.familyId, childId, payload);
    if (res?.success) {
        applyPayloadToState(payload, childId);
    }
    return res;
}

export async function saveChildProfileInline() {
    const childName = document.getElementById('settings-child-name-inline')?.value.trim();
    if (!childName) {
        return showToast('Введите имя ребенка', 'error');
    }

    try {
        const res = await persistChildSettings({ name: childName });
        if (!res?.success) throw new Error(res?.error || 'Ошибка обновления');
        showToast('Информация обновлена', 'success');
        renderAll();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

function parseLimitValue(element, label) {
    if (!element) return null;
    const raw = element.value?.trim();
    if (!raw) return null;
    const parsed = parseInt(raw, 10);
    if (Number.isNaN(parsed) || parsed < 0) {
        throw new Error(`Некорректный лимит ${label}`);
    }
    return parsed;
}

export async function saveChildLimitsInline() {
    const monthlyEl = document.getElementById('settings-child-monthly-limit-inline');
    const dayEl = document.getElementById('settings-child-day-coin-limit-inline');
    const payload = {};

    try {
        const monthlyValue = parseLimitValue(monthlyEl, 'денег');
        if (monthlyValue !== null) payload.monthly_limit = monthlyValue;
        const dayValue = parseLimitValue(dayEl, 'монет');
        if (dayValue !== null) payload.daily_coin_limit = dayValue;

        if (!Object.keys(payload).length) {
            return showToast('Укажите хотя бы один лимит', 'error');
        }

        const res = await persistChildSettings(payload);
        if (!res?.success) throw new Error(res?.error || 'Ошибка сохранения лимитов');
        showToast('Лимиты сохранены', 'success');
        renderAll();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

export async function refreshChildLinkInline() {
    const input = document.getElementById('settings-child-link-input-inline');
    if (!input) return;
    const targetId = getTargetChildId();
    if (!targetId) return input.value = 'Сначала добавьте ребенка';

    try {
        const data = await getChildLink(targetId);
        if (data.link) input.value = data.link;
    } catch (err) {
        console.error(err);
    }
}

export async function copyChildLinkInline() {
    const input = document.getElementById('settings-child-link-input-inline');
    if (input?.value) {
        input.select();
        try {
            document.execCommand('copy');
            showToast('Ссылка скопирована!', 'success');
            const status = document.getElementById('child-link-status');
            if (status) {
                status.classList.remove('hidden');
                setTimeout(() => status.classList.add('hidden'), 3000);
            }
        } catch (err) {
            showToast('Не удалось скопировать', 'error');
        }
    }
}

export async function regenerateChildLinkInline() {
    if (!(await showConfirm('Обновление ссылки', 'Вы уверены? Старая ссылка перестанет работать.'))) return;
    const childId = getTargetChildId();
    if (!childId) return showToast('Нет выбранного ребенка', 'error');

    try {
        const data = await regenerateChildToken(childId);
        if (data.link) {
            const input = document.getElementById('settings-child-link-input-inline');
            if (input) input.value = data.link;
            showToast('Ссылка обновлена', 'success');
        } else {
            showToast('Ошибка при обновлении ссылки', 'error');
        }
    } catch (err) {
        showToast('Ошибка сети', 'error');
    }
}
