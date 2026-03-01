/** @file Admin Children frontend UI module */
import { state, setState } from './state.js';
import { addChild } from './api.js';
import { renderAll } from './ui.js';
import { showToast, closeModal, openModal } from './utils.js';
import { refreshChildLinkInline } from './admin-settings.js';
import { useChildTheme } from './age-theme.js';

function getNumericLimit(value, fallback) {
    const n = Number(value);
    return Number.isFinite(n) ? n : fallback;
}

function getMonthlyLimit(child) {
    return getNumericLimit(child?.monthlyLimit ?? child?.monthly_limit, 10000);
}

function getDailyLimit(child) {
    return getNumericLimit(child?.dailyCoinLimit ?? child?.daily_coin_limit, 0);
}

function updateSettingsFields(child) {
    const fields = {
        'settings-child-name-inline': child.name,
        'settings-child-monthly-limit-inline': getMonthlyLimit(child),
        'settings-child-day-coin-limit-inline': getDailyLimit(child)
    };
    Object.keys(fields).forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = fields[id];
    });
}

export function switchChild(childId) {
    if (!childId) return;
    const child = state.children.find(c => c.id == childId);
    setState({
        currentChildId: childId,
        balance: child?.balance || 0,
        monthlyLimit: getMonthlyLimit(child),
        dailyCoinLimit: getDailyLimit(child)
    });

    renderAll();

    const analytics = document.getElementById('analytics-section');
    if (analytics && !analytics.classList.contains('hidden')) {
        import('./analytics-ui.js').then(m => m.loadAnalytics());
    }

    if (child) {
        updateSettingsFields(child);
        refreshChildLinkInline();
    }
    useChildTheme(childId);
}

export function openAddChildModal() {
    document.getElementById('new-child-name').value = '';
    openModal('add-child-modal');
}

export async function saveNewChild() {
    const name = document.getElementById('new-child-name').value.trim();
    if (!name) return showToast('Введите имя', 'error');

    const result = await addChild(name);
    if (result.success) {
        showToast('Ребенок добавлен!', 'success');
        closeModal('add-child-modal');
        window.location.reload();
    } else {
        showToast('Ошибка: ' + (result.error || 'Не удалось добавить'), 'error');
    }
}
