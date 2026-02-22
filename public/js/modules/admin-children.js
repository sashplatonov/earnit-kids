import { state, setState } from './state.js';
import { addChild } from './api.js';
import { renderAll } from './ui.js';
import { showToast, closeModal, openModal } from './utils.js';
import { refreshChildLinkInline } from './admin-settings.js';

function updateSettingsFields(child) {
    const fields = {
        'settings-child-name-inline': child.name,
        'settings-money-limit-inline': child.monthlyLimit ?? 10000,
        'settings-day-coin-limit-inline': child.dailyCoinLimit ?? 0
    };
    Object.keys(fields).forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = fields[id];
    });
}

export function switchChild(childId) {
    const child = state.children.find(c => c.id == childId);
    setState({
        currentChildId: childId,
        balance: child?.balance || 0,
        monthlyLimit: child?.monthlyLimit ?? 10000,
        dailyCoinLimit: child?.dailyCoinLimit ?? 0
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
