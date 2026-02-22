import { state } from './state.js';
import { renderAll } from './ui.js';
import { showToast, closeModal, openModal } from './utils.js';
import { scheduleSave, buyItem, earnCoins, requestCoins, deleteHistoryItem, approveRequest, rejectRequest, deleteRequest, adminAwardCoins } from './actions.js';
import { initializePushNotifications, setPushRefreshHandler } from './push.js';
import { startIosDevFallback } from './ios-dev-fallback.js';
import { setupPullToRefresh } from './pull-to-refresh.js';
import * as admin from './admin.js';
import { renderRules, openEditRules, saveRules } from './rules.js';
import { handleSearch, addNewFriend, saveNickname } from './friends.js';
import { loadAnalytics } from './analytics-ui.js';
import { renderCatalog, addCatalogItem } from './main-catalog.js';
import { setupTabControls } from './main-tabs.js';
import { initializeFromServer, refreshFromServerAndRender, setupCommonControls } from './main-init.js';

async function loadAbout() {
    const container = document.getElementById('about-content');
    if (!container) return;
    try {
        const res = await fetch('/about.html');
        if (!res.ok) throw new Error('Not found');
        const text = await res.text();
        const doc = new DOMParser().parseFromString(text, 'text/html');
        container.innerHTML = (doc.getElementById('about-landing-style')?.outerHTML || '') + (doc.getElementById('about-landing-content')?.outerHTML || '<p>Контент не найден</p>');
    } catch (err) { container.innerHTML = '<p>Ошибка загрузки содержания</p>'; }
}

function setupAdminUI() {
    if (!state.isAdmin) return;
    document.getElementById('edit-rules-btn')?.addEventListener('click', openEditRules);
    ['edit-rules-btn', 'nav-catalog', 'nav-child-link'].forEach(id => {
        const el = document.getElementById(id);
        if (el) { el.classList.remove('hidden'); el.parentElement?.classList.remove('hidden'); }
    });
    renderCatalog();
}

function setupSpecificControls() {
    const bind = (id, fn, evt = 'click') => document.getElementById(id)?.addEventListener(evt, fn);
    bind('settings-change-pin-btn', admin.openChangePinModal);
    bind('settings-save-main-btn', admin.saveFamilySettingsInline);
    bind('settings-save-pin-btn', admin.saveNewPinInline);
    bind('settings-copy-link-btn', admin.copyChildLinkInline);
    bind('settings-regenerate-link-btn', admin.regenerateChildLinkInline);
    bind('settings-save-nickname-btn', saveNickname);
    bind('add-task-btn', admin.openTaskModal);
    bind('task-save', admin.saveTask);
    bind('task-cancel', () => closeModal('task-modal'));
    bind('task-delete', admin.deleteTask);
    bind('add-shop-btn', admin.openShopModal);
    bind('shop-save', admin.saveShopItem);
    bind('shop-cancel', () => closeModal('shop-modal'));
    bind('shop-delete', admin.deleteShopItem);
    bind('rules-save', saveRules);
    bind('rules-cancel', () => closeModal('rules-modal'));
    bind('catalog-age-min-filter', renderCatalog, 'input');
    bind('catalog-age-max-filter', renderCatalog, 'input');
}

window.app = {
    buyItem, earnCoins, requestCoins, editTask: admin.editTask, editShopItem: admin.editShopItem,
    deleteHistoryItem, approveRequest, rejectRequest, deleteRequest, addCatalogItem,
    openFamilySettingsModal: admin.openFamilySettingsModal, saveFamilySettings: admin.saveFamilySettings,
    saveFamilySettingsInline: admin.saveFamilySettingsInline, saveNewPinInline: admin.saveNewPinInline,
    copyChildLinkInline: admin.copyChildLinkInline, regenerateChildLinkInline: admin.regenerateChildLinkInline,
    switchChild: admin.switchChild, openAddChildModal: admin.openAddChildModal, addNewFriend, handleSearch,
    saveNickname, adminAwardCoins, loadAnalytics
};

async function initializeApp() {
    try {
        const data = await initializeFromServer();
        await initializePushNotifications();
        setPushRefreshHandler(() => refreshFromServerAndRender(false));
        startIosDevFallback(data, () => refreshFromServerAndRender(false));

        renderAll(); renderRules(); loadAbout();
        if (state.isAdmin) setupAdminUI();
        document.getElementById('nav-settings')?.classList.remove('hidden');

        setupCommonControls(); setupSpecificControls();
        setupPullToRefresh(() => refreshFromServerAndRender(true));
        setupTabControls();
        document.querySelectorAll('.modal__backdrop').forEach(b => b.addEventListener('click', () => b.closest('.modal')?.classList.remove('active')));
    } catch (err) {
        console.error('App init error:', err);
    } finally {
        document.querySelector('.nav')?.classList.remove('nav--pending');
    }
}

document.addEventListener('DOMContentLoaded', () => initializeApp().catch(err => {
    console.error('App init failed:', err);
    showToast('Ошибка инициализации приложения', 'error');
}));
