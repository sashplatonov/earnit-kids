/** @file Main frontend UI module */
import { state } from './state.js';
import { renderAll, renderShop, renderTasks } from './ui.js';
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
import { setupPwaInstall } from './pwa-install.js';
import { setupAgeThemeControls, useChildTheme } from './age-theme.js';

const MOBILE_LAYOUT_QUERY = '(max-width: 900px)';

function applyMobileViewportBudgets() {
    const root = document.documentElement;
    if (!window.matchMedia(MOBILE_LAYOUT_QUERY).matches) {
        root.style.removeProperty('--mobile-header-max-height');
        root.style.removeProperty('--mobile-nav-height');
        root.style.removeProperty('--mobile-main-bottom-offset');
        return;
    }

    const viewportHeight = Math.max(window.innerHeight || 0, 0);
    if (!viewportHeight) return;

    const headerBudget = Math.round(viewportHeight * 0.12);
    const navBudget = Math.round(viewportHeight * 0.10);
    const navHeight = Math.max(56, Math.min(72, navBudget));

    root.style.setProperty('--mobile-header-max-height', `${Math.max(72, headerBudget)}px`);
    root.style.setProperty('--mobile-nav-height', `${navHeight}px`);
    root.style.setProperty('--mobile-main-bottom-offset', `${navHeight + 60}px`);
}

function setupMobileViewportBudgets() {
    applyMobileViewportBudgets();
    window.addEventListener('resize', applyMobileViewportBudgets, { passive: true });
    window.addEventListener('orientationchange', applyMobileViewportBudgets, { passive: true });
}

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
    const controls = [
        { id: 'settings-change-pin-btn', fn: admin.openChangePinModal },
        { id: 'settings-save-profile-btn', fn: admin.saveChildProfileInline },
        { id: 'settings-save-limits-btn', fn: admin.saveChildLimitsInline },
        { id: 'settings-save-pin-btn', fn: admin.saveNewPinInline },
        { id: 'settings-copy-link-btn', fn: admin.copyChildLinkInline },
        { id: 'settings-regenerate-link-btn', fn: admin.regenerateChildLinkInline },
        { id: 'settings-save-nickname-btn', fn: saveNickname },
        { id: 'add-task-btn', fn: admin.openTaskModal },
        { id: 'task-save', fn: admin.saveTask },
        { id: 'task-cancel', fn: () => closeModal('task-modal') },
        { id: 'task-delete', fn: admin.deleteTask },
        { id: 'add-shop-btn', fn: admin.openShopModal },
        { id: 'wizard-add-task', fn: admin.openTaskModal },
        { id: 'shop-save', fn: admin.saveShopItem },
        { id: 'shop-cancel', fn: () => closeModal('shop-modal') },
        { id: 'shop-delete', fn: admin.deleteShopItem },
        { id: 'wizard-add-shop', fn: admin.openShopModal },
        { id: 'rules-save', fn: saveRules },
        { id: 'rules-cancel', fn: () => closeModal('rules-modal') },
        { id: 'catalog-age-min-filter', fn: renderCatalog, evt: 'input' },
        { id: 'catalog-age-max-filter', fn: renderCatalog, evt: 'input' }
    ];
    controls.forEach(({ id, fn, evt }) => bind(id, fn, evt));
}

const CARD_SHORTCUTS_KEY = '__earnitCardShortcuts';

function ensureCardShortcuts() {
    if (typeof window === 'undefined') {
        return { task: new Set(), shop: new Set() };
    }
    if (!window[CARD_SHORTCUTS_KEY]) {
        window[CARD_SHORTCUTS_KEY] = {
            task: new Set(),
            shop: new Set()
        };
    }
    if (!(window[CARD_SHORTCUTS_KEY].shop instanceof Set)) {
        const legacy = window[CARD_SHORTCUTS_KEY].shop;
        window[CARD_SHORTCUTS_KEY].shop = new Set([
            ...(legacy?.quick ? Array.from(legacy.quick) : []),
            ...(legacy?.bookmark ? Array.from(legacy.bookmark) : [])
        ]);
    }
    return window[CARD_SHORTCUTS_KEY];
}

function parseShortcutKind(type) {
    if (type === 'shop' || type === 'shop_quick') return { normalized: 'shop' };
    return { normalized: 'task', mode: 'quick' };
}

function getShortcutBucket(shortcuts, kind) {
    return kind.normalized === 'shop' ? shortcuts.shop : shortcuts.task;
}

function updateShortcutButton(trigger, kind, isActive) {
    if (!trigger) return;
    trigger.setAttribute('aria-pressed', String(isActive));
    trigger.classList.toggle('card__quick-bookmark--active', isActive);
    if (kind.normalized === 'shop') {
        trigger.textContent = isActive ? '⚡ В быстром' : '⚡ В быстрый';
        return;
    }
    trigger.textContent = isActive ? '⭐ В быстрых' : '☆ В быстрые';
}

function getShortcutToast(kind, wasActive) {
    const bucketLabel = kind.normalized === 'shop' ? 'быстрый' : 'быстрые действия';
    return wasActive ? `Убрано: ${bucketLabel}` : `Добавлено: ${bucketLabel}`;
}

function toggleCardBookmark(type, id, trigger) {
    const kind = parseShortcutKind(type);
    const shortcuts = ensureCardShortcuts();
    const bucket = getShortcutBucket(shortcuts, kind);
    const numericId = Number(id);
    const wasActive = bucket.has(numericId);
    if (wasActive) {
        bucket.delete(numericId);
    } else {
        bucket.add(numericId);
    }
    const isActive = !wasActive;
    const card = document.querySelector(`.card--${kind.normalized}[data-id=\"${numericId}\"]`);
    card?.classList.toggle('card--highlight', isActive);
    updateShortcutButton(trigger, kind, isActive);
    if (kind.normalized === 'shop') renderShop();
    else renderTasks();
    showToast(getShortcutToast(kind, wasActive), wasActive ? 'info' : 'success');
}

window.app = {
    buyItem, earnCoins, requestCoins, editTask: admin.editTask, editShopItem: admin.editShopItem,
    deleteHistoryItem, approveRequest, rejectRequest, deleteRequest, addCatalogItem,
    saveNewPinInline: admin.saveNewPinInline,
    copyChildLinkInline: admin.copyChildLinkInline, regenerateChildLinkInline: admin.regenerateChildLinkInline,
    switchChild: admin.switchChild, openAddChildModal: admin.openAddChildModal,
    openTaskModal: admin.openTaskModal, openShopModal: admin.openShopModal,
    addNewFriend, handleSearch, saveNickname, adminAwardCoins,
    toggleCardBookmark,
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
    setupMobileViewportBudgets();
    showSkeletons();
    setupPwaInstall();

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
    setupAgeThemeControls(() => state.currentChildId || state.children[0]?.id || null);
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

function getBuildVersion() {
    return document.querySelector('meta[name="app-build-version"]')?.content?.trim() || '';
}

function getServiceWorkerUrl() {
    const buildVersion = getBuildVersion();
    if (!buildVersion) return '/sw.js';
    return `/sw.js?v=${encodeURIComponent(buildVersion)}`;
}

function requestImmediateActivation(registration) {
    if (!registration || !registration.waiting) return;
    registration.waiting.postMessage({ type: 'SKIP_WAITING' });
}

function setupServiceWorkerAutoReload() {
    let refreshing = false;
    navigator.serviceWorker.addEventListener('controllerchange', () => {
        if (refreshing) return;
        refreshing = true;
        window.location.reload();
    });
}

function setupServiceWorkerUpdateChecks(registration) {
    const UPDATE_INTERVAL_MS = 5 * 60 * 1000;
    const safeUpdate = () => registration.update().catch(err => console.log('SW update check failed:', err));

    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') safeUpdate();
    });

    window.addEventListener('focus', safeUpdate);
    window.setInterval(safeUpdate, UPDATE_INTERVAL_MS);
}

function setupServiceWorkerLifecycle(registration) {
    requestImmediateActivation(registration);

    registration.addEventListener('updatefound', () => {
        const installing = registration.installing;
        if (!installing) return;
        installing.addEventListener('statechange', () => {
            if (installing.state === 'installed' && navigator.serviceWorker.controller) {
                requestImmediateActivation(registration);
            }
        });
    });
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
    setupServiceWorkerAutoReload();

    try {
        const registration = await navigator.serviceWorker.register(getServiceWorkerUrl());
        setupServiceWorkerLifecycle(registration);
        setupServiceWorkerUpdateChecks(registration);
    } catch (err) {
        console.log('SW registration failed:', err);
    }
}

function handleMissingData() {
    document.querySelector('.nav')?.classList.remove('nav--pending');
    showToast('Не удалось получить данные магазина. Повторите попытку позже.', 'error');
}

document.addEventListener('DOMContentLoaded', () => initializeApp().catch(err => {
    console.error('App init failed:', err);
    showToast('Ошибка инициализации приложения', 'error');
}));
