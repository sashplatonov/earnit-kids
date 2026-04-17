/** @file Admin Children frontend UI module */
import { state, setState } from './state.js';
import { addChild, loadDataFromServer, savePreference } from './api.js';
import { applyServerFamilyData, flushPendingSave } from './action-helpers.js';
import { renderAll } from './ui.js';
import { showToast, closeModal, openModal } from './utils.js';
import { refreshChildLinkInline } from './admin-settings.js';
import { useChildTheme } from './age-theme.js';

function getNumericLimit(value, fallback) {
    const n = Number(value);
    return Number.isFinite(n) ? n : fallback;
}

function getMonthlyLimit(child) {
    return getNumericLimit(child?.monthlyLimit, 10000);
}

function getDailyLimit(child) {
    return getNumericLimit(child?.dailyCoinLimit, 0);
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

function applyServerChildScope(data, fallbackChild) {
    return applyServerFamilyData(data, { currentChildId: fallbackChild?.id ?? state.currentChildId });
}

function persistPreferenceIfNeeded(childId, options) {
    if (options?.persistPreference !== false) {
        void savePreference('lastSelectedChildId', childId);
    }
}

function setPreSwitchState(childId, child) {
    setState({
        currentChildId: childId,
        balance: child?.balance || 0,
        monthlyLimit: getMonthlyLimit(child),
        dailyCoinLimit: getDailyLimit(child),
        childNickname: child?.name ?? state.childNickname
    });
    localStorage.setItem('earnit-last-child-id', childId);
}

async function fetchAndApplyServerData(childId, child) {
    const data = await loadDataFromServer(childId);
    if (!data) return null;
    return applyServerChildScope(data, child);
}

function handleLoadFailure(previousSelection) {
    setState(previousSelection);
    renderAll();
    showToast('Не удалось переключить ребенка', 'error');
}

function maybeLoadAnalytics() {
    const analytics = document.getElementById('analytics-section');
    if (analytics && !analytics.classList.contains('hidden')) {
        import('./analytics-ui.js').then(m => m.loadAnalytics());
    }
}

export async function switchChild(childId, options = {}) {
    if (!childId) return false;

    await flushPendingSave();

    let child = state.children.find(c => c.id == childId);
    const previousSelection = {
        currentChildId: state.currentChildId,
        balance: state.balance,
        monthlyLimit: state.monthlyLimit,
        dailyCoinLimit: state.dailyCoinLimit,
        childNickname: state.childNickname
    };

    setPreSwitchState(childId, child);
    persistPreferenceIfNeeded(childId, options);

    const applied = await fetchAndApplyServerData(childId, child);
    if (!applied) {
        handleLoadFailure(previousSelection);
        return false;
    }

    child = applied;
    renderAll();
    maybeLoadAnalytics();

    if (child) {
        updateSettingsFields(child);
        refreshChildLinkInline();
    }
    useChildTheme(childId, child?.theme);

    return true;
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
