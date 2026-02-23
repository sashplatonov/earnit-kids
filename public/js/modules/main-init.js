import { state, setState } from './state.js';
import { loadDataFromServer, loadBaseData, logout } from './api.js';
import { renderAll } from './ui.js';
import { renderCatalog } from './main-catalog.js';
import { showToast, handleConfirm, closeModal } from './utils.js';
import { initializePushNotifications, setPushRefreshHandler, unregisterPushNotifications } from './push.js';
import { startIosDevFallback, stopIosDevFallback } from './ios-dev-fallback.js';
import { setupPullToRefresh } from './pull-to-refresh.js';
import { switchChild, saveNewChild } from './admin.js';
import { setupTabControls } from './main-tabs.js';
import { handleSearch } from './friends.js';
import { scheduleSave } from './actions.js';

export function buildInitialState(data, baseData) {
    const defaults = {
        familyId: null, balance: 0, tasks: [], shopItems: [], history: [],
        requests: [], familyName: '', childNickname: null, monthlyLimit: 10000,
        dailyCoinLimit: 0, children: []
    };

    const s = {
        isAdmin: Boolean(data.isAdmin),
        role: data.isAdmin ? 'admin' : 'child',
        baseData
    };

    // Use loop or assign to avoid complexity from many ??
    Object.keys(defaults).forEach(key => {
        const dataKey = key === 'shopItems' ? 'shop' : key;
        s[key] = data[dataKey] ?? defaults[key];
    });

    return s;
}

export async function initializeFromServer() {
    const data = await loadDataFromServer();
    if (!data) return showToast('Не удалось загрузить данные', 'error') || null;
    const baseData = data.isAdmin ? (await loadBaseData() || { tasks: [], products: [] }) : { tasks: [], products: [] };
    setState(buildInitialState(data, baseData));
    if (data.isAdmin && state.children?.length > 0 && !state.currentChildId) switchChild(state.children[0].id);
    return data;
}

export async function refreshFromServerAndRender(showSuccess = false) {
    const lists = ['tasks-list', 'shop-list', 'history-list', 'requests-list'];
    lists.forEach(id => {
        const el = document.getElementById(id);
        if (el && el.innerHTML === '') { // Only if empty, to avoid flickering if already rendered
            el.innerHTML = Array(3).fill('<div class="card skeleton" style="min-height: 120px; width: 100%;">Загрузка</div>').join('');
        }
    });

    const data = await initializeFromServer();
    if (!data) return false;
    renderAll();
    if (state.isAdmin) renderCatalog();
    if (showSuccess) showToast('Данные обновлены', 'success');
    return true;
}

export function setupCommonControls() {
    const bind = (id, fn) => document.getElementById(id)?.addEventListener('click', fn);
    bind('logout-btn', async () => {
        stopIosDevFallback(); await unregisterPushNotifications();
        if (await logout()) window.location.reload(); else showToast('Ошибка при выходе', 'error');
    });
    bind('refresh-data-btn', () => refreshFromServerAndRender(true));
    bind('confirm-ok', handleConfirm);
    bind('confirm-cancel', () => closeModal('confirm-modal'));
    bind('add-child-save', saveNewChild);
    bind('add-child-cancel', () => closeModal('add-child-modal'));
    bind('friend-search-btn', handleSearch);
    bind('clear-history-btn', () => {
        if (confirm('Очистить ВСЮ историю?')) { setState({ history: [] }); scheduleSave(); renderAll(); }
    });
}
