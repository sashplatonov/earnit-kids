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
import { renderCatalog, addCatalogItem } from './main-catalog.js';
import { setupTabControls } from './main-tabs.js';
import { initializeFromServer, refreshFromServerAndRender, setupCommonControls } from './main-init.js';
import { initializeWebSocket } from './websocket.js';

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
    bind('settings-save-child-btn', admin.saveChildSettingsInline);
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
    saveNickname, adminAwardCoins,
    loadAnalytics: (...args) => import('./analytics-ui.js').then(m => m.loadAnalytics(...args))
};

function showSkeletons() {
    const lists = ['tasks-list', 'shop-list', 'history-list', 'requests-list'];
    lists.forEach(id => {
        const el = document.getElementById(id);
        if (el) el.innerHTML = Array(3).fill('<div class="card skeleton" style="min-height: 120px; width: 100%;">Загрузка</div>').join('');
    });
}

async function initializeApp() {
    showSkeletons();

    setupTabControls(); // Always bind tabs first to prevent long scroll on error

    try {
        const data = await initializeFromServer();
        if (!data) {
            handleMissingData();
            return;
        }

        await startBackgroundServices(data);
        renderInitialViews();
        setupControlsAndRefresh();
        await registerServiceWorker();
    } catch (err) {
        console.error('App init error:', err);
    } finally {
        document.querySelector('.nav')?.classList.remove('nav--pending');
    }
}

async function startBackgroundServices(data) {
    await safeRun(initializePushNotifications, 'Push init failed:');
    await safeRun(initializeWebSocket, 'WS init failed:');
    setPushRefreshHandler(() => refreshFromServerAndRender(false));
    await safeRun(() => startIosDevFallback(data, () => refreshFromServerAndRender(false)), 'Fallback init failed:');
}

async function safeRun(fn, message) {
    try {
        await fn();
    } catch (err) {
        console.error(message, err);
    }
}

function renderInitialViews() {
    renderAll();
    renderRules();
    loadAbout();
    if (state.isAdmin) setupAdminUI();
    document.getElementById('nav-settings')?.classList.remove('hidden');
}

function setupControlsAndRefresh() {
    setupCommonControls();
    setupSpecificControls();
    setupPullToRefresh(() => refreshFromServerAndRender(true));
}

function isLocalhost() {
    const host = window.location.hostname;
    return host === 'localhost' || host === '127.0.0.1' || host === '::1';
}

async function disableLocalhostCaching() {
    if ('serviceWorker' in navigator) {
        try {
            const registrations = await navigator.serviceWorker.getRegistrations();
            await Promise.all(registrations.map((registration) => registration.unregister()));
        } catch (err) {
            console.log('SW unregister failed:', err);
        }
    }

    if ('caches' in window) {
        try {
            const keys = await caches.keys();
            await Promise.all(keys.map((key) => caches.delete(key)));
        } catch (err) {
            console.log('Cache cleanup failed:', err);
        }
    }
}

async function registerServiceWorker() {
    if (!('serviceWorker' in navigator)) return;
    if (isLocalhost()) return disableLocalhostCaching();
    navigator.serviceWorker.register('/sw.js').catch(err => console.log('SW registration failed:', err));
}

function handleMissingData() {
    document.querySelector('.nav')?.classList.remove('nav--pending');
    showToast('Не удалось получить данные магазина. Повторите попытку позже.', 'error');
}

document.addEventListener('DOMContentLoaded', () => initializeApp().catch(err => {
    console.error('App init failed:', err);
    showToast('Ошибка инициализации приложения', 'error');
}));
