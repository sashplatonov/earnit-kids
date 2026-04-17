/** @file Main Init frontend UI module */
import { state, setState } from './state.js';
import { loadDataFromServer, loadBaseData, logout } from './api.js';
import { renderAll } from './ui.js';
import { renderCatalog } from './main-catalog.js';
import { showToast, handleConfirm, handleCancel, closeModal, showConfirm } from './utils.js';
import { initializePushNotifications, setPushRefreshHandler, unregisterPushNotifications } from './push.js';
import { startIosDevFallback, stopIosDevFallback } from './ios-dev-fallback.js';
import { setupPullToRefresh } from './pull-to-refresh.js';
import { switchChild, saveNewChild } from './admin.js';
import { setupTabControls } from './main-tabs.js';
import { handleSearch } from './friends.js';
import { scheduleSave, flushPendingSave } from './actions.js';
import { normalizeServerData } from './server-contract.js';

function parseBoolean(value) {
    return value === true || value === 'true' || value === 1 || value === '1';
}

export function buildInitialState(data, baseData) {
    const normalizedData = normalizeServerData(data);
    const defaults = {
        familyId: null, balance: 0, tasks: [], shopItems: [], history: [],
        requests: [], childNickname: null, monthlyLimit: 10000,
        dailyCoinLimit: 0, children: []
    };

    const isAdminFlag = parseBoolean(normalizedData.isAdmin);
    const s = {
        isAdmin: isAdminFlag,
        role: isAdminFlag ? 'admin' : null,
        baseData,
        isLoading: false
    };

    // Use loop or assign to avoid complexity from many ??
    Object.keys(defaults).forEach(key => {
        const dataKey = key === 'shopItems' ? 'shop' : key;
        s[key] = normalizedData[dataKey] ?? defaults[key];
    });

    return s;
}

export async function initializeFromServer() {
    const data = await loadDataFromServer();
    if (!data) return showToast('Не удалось загрузить данные', 'error') || null;
    const isAdminFlag = parseBoolean(data.isAdmin);
    const baseData = isAdminFlag ? (await loadBaseData() || { tasks: [], products: [] }) : { tasks: [], products: [] };
    setState(buildInitialState(data, baseData));
    if (isAdminFlag && state.children?.length > 0) {
        const serverChildId = data.lastSelectedChildId;
        const localChildId = localStorage.getItem('earnit-last-child-id');
        const preferredId = serverChildId || localChildId;
        const childToSelect = state.children.find(c => c.id == preferredId) || state.children[0];
        await switchChild(childToSelect.id, { persistPreference: false });
    }
    return data;
}

export async function refreshFromServerAndRender(showSuccess = false) {
    await flushPendingSave();

    const lists = ['tasks-list', 'shop-list', 'history-list', 'requests-list'];
    lists.forEach(id => {
        const el = document.getElementById(id);
        if (el && el.innerHTML === '') { // Only if empty, to avoid flickering if already rendered
            el.innerHTML = Array(3).fill('<div class="card skeleton" style="min-height: 120px; width: 100%;"></div>').join('');
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
    bind('confirm-cancel', handleCancel);
    bind('add-child-save', saveNewChild);
    bind('add-child-cancel', () => closeModal('add-child-modal'));
    bind('friend-search-btn', handleSearch);
    bind('clear-history-btn', () => {
        showConfirm('Очистка истории', 'Очистить ВСЮ историю?', {
            onConfirm: () => { setState({ history: [] }); scheduleSave(); renderAll(); }
        });
    });
}
