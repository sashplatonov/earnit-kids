/** @file Admin Settings frontend UI module */
import { state, setState } from './state.js';
import { updateChildSettings, getChildLink, regenerateChildToken } from './api.js';
import { renderAll } from './ui.js';
import { showToast } from './utils.js';

async function updateCurrentChildSettings() {
    const childName = document.getElementById('settings-child-name-inline')?.value.trim();
    const monthlyLimitValue = parseInt(document.getElementById('settings-child-monthly-limit-inline')?.value);
    const dayLimitValue = parseInt(document.getElementById('settings-child-day-coin-limit-inline')?.value);

    const payload = {
        name: childName,
        monthly_limit: isNaN(monthlyLimitValue) ? 0 : monthlyLimitValue,
        daily_coin_limit: isNaN(dayLimitValue) ? 0 : dayLimitValue
    };

    const res = await updateChildSettings(state.familyId, state.currentChildId, payload);

    if (res.success) {
        const child = state.children.find(c => c.id == state.currentChildId);
        if (child) {
            child.name = childName;
            child.monthlyLimit = payload.monthly_limit;
            child.dailyCoinLimit = payload.daily_coin_limit;
        }
        setState({
            monthlyLimit: payload.monthly_limit,
            dailyCoinLimit: payload.daily_coin_limit
        });
    }

    return res;
}

export async function saveChildSettingsInline() {
    if (!state.currentChildId) {
        return showToast('Сначала выберите ребенка', 'error');
    }

    try {
        const childRes = await updateCurrentChildSettings();
        if (!childRes?.success) throw new Error(childRes?.error || 'Ошибка обновления ребенка');
        showToast('Настройки ребенка обновлены!', 'success');
        renderAll();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

export async function refreshChildLinkInline() {
    const input = document.getElementById('settings-child-link-input-inline');
    if (!input) return;
    const targetId = state.currentChildId || (state.children[0]?.id || null);
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
    if (!confirm('Вы уверены? Старая ссылка перестанет работать.')) return;
    const childId = state.currentChildId || (state.children[0]?.id || null);
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
