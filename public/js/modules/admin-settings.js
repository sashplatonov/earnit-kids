import { state, setState } from './state.js';
import { updateFamilySettingsOnServer, updateChildSettings, getChildLink, regenerateChildToken } from './api.js';
import { renderAll } from './ui.js';
import { showToast, closeModal, openModal } from './utils.js';

export function openFamilySettingsModal() {
    document.getElementById('settings-family-name').value = state.familyName || '';
    document.getElementById('settings-money-limit').value = state.monthlyLimit || 10000;
    openModal('family-settings-modal');
}

export async function saveFamilySettings() {
    const name = document.getElementById('settings-family-name').value.trim();
    const monthlyLimit = parseInt(document.getElementById('settings-money-limit').value);
    if (!name) return showToast('Название не может быть пустым', 'error');

    const res = await updateFamilySettingsOnServer({ name, monthly_limit: monthlyLimit });
    if (res?.success) {
        setState({ familyName: name });
        showToast('Настройки обновлены!', 'success');
        closeModal('family-settings-modal');
    } else showToast('Ошибка при обновлении настроек', 'error');
}

async function updateCurrentChildSettings() {
    const childName = document.getElementById('settings-child-name-inline')?.value.trim();
    const mLimit = parseInt(document.getElementById('settings-money-limit-inline')?.value);
    const dayLimit = parseInt(document.getElementById('settings-day-coin-limit-inline')?.value);

    const res = await updateChildSettings(state.familyId, state.currentChildId, {
        name: childName,
        monthly_limit: isNaN(mLimit) ? 0 : mLimit,
        daily_coin_limit: isNaN(dayLimit) ? 0 : dayLimit
    });

    if (res.success) {
        const child = state.children.find(c => c.id == state.currentChildId);
        if (child) {
            child.name = childName;
            child.monthlyLimit = isNaN(mLimit) ? 0 : mLimit;
            child.dailyCoinLimit = isNaN(dayLimit) ? 0 : dayLimit;
        }
    } else throw new Error('Ошибка обновления ребенка');
}

export async function saveFamilySettingsInline() {
    const name = document.getElementById('settings-family-name-inline').value.trim();
    if (!name) return showToast('Название не может быть пустым', 'error');

    try {
        const familyRes = await updateFamilySettingsOnServer({ name });
        if (!familyRes?.success) throw new Error('Ошибка обновления семьи');
        setState({ familyName: name });

        if (state.currentChildId) await updateCurrentChildSettings();

        showToast('Настройки обновлены!', 'success');
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
    } catch (err) { console.error(err); }
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
        } catch (err) { showToast('Не удалось скопировать', 'error'); }
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
        } else showToast('Ошибка при обновлении ссылки', 'error');
    } catch (err) { showToast('Ошибка сети', 'error'); }
}
