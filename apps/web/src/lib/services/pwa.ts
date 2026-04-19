import { showToast } from '$lib/stores/toasts';

const MOBILE_QUERY = '(max-width: 900px)';
const PTR_TRIGGER_DISTANCE = 72;
const PTR_MAX_PULL = 110;

type RefreshCallback = () => Promise<unknown> | unknown;
type CleanupFn = () => void;
type BeforeInstallPromptEvent = Event & {
    prompt: () => Promise<void>;
    userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
};

const ptrState = {
    indicator: null as HTMLElement | null,
    indicatorText: null as HTMLElement | null,
    refreshCallback: null as RefreshCallback | null,
    startY: null as number | null,
    pullDistance: 0,
    pulling: false,
    refreshing: false,
};

function isLocalhost() {
    const host = window.location.hostname;
    return host === 'localhost' || host === '127.0.0.1' || host === '::1';
}

function getBuildVersion() {
    return document.querySelector('meta[name="app-build-version"]')?.getAttribute('content')?.trim() || '';
}

function getServiceWorkerUrl() {
    const buildVersion = getBuildVersion();
    if (!buildVersion) return '/sw.js';
    return `/sw.js?v=${encodeURIComponent(buildVersion)}`;
}

function isMobileViewport() {
    return typeof window !== 'undefined'
        && typeof window.matchMedia === 'function'
        && window.matchMedia(MOBILE_QUERY).matches;
}

function getUa() {
    return (navigator.userAgent || '').toLowerCase();
}

function isIosMobile() {
    return /iphone|ipad|ipod/.test(getUa());
}

function isAndroidMobile() {
    return /android/.test(getUa());
}

function isStandaloneMode() {
    const isMediaStandalone = typeof window.matchMedia === 'function'
        && window.matchMedia('(display-mode: standalone)').matches;
    return isMediaStandalone || (window.navigator as Navigator & { standalone?: boolean }).standalone === true;
}

function hideInstallUi(button: HTMLElement, iosHint: HTMLElement) {
    button.classList.add('hidden');
    iosHint.classList.add('hidden');
    iosHint.textContent = '';
}

function showIosInstructions(iosHint: HTMLElement) {
    iosHint.textContent = 'iOS: Поделиться -> На экран Домой.';
    iosHint.classList.remove('hidden');
}

function shouldSkipInstallUi(isMobile: boolean, isIos: boolean, isAndroid: boolean) {
    return !isMobile || (!isIos && !isAndroid) || isStandaloneMode();
}

function setupOfflineStatusBanner(): CleanupFn {
    const banner = document.getElementById('offline-status-banner');
    if (!banner) return () => {};

    const update = () => {
        const isOffline = navigator.onLine === false;
        banner.classList.toggle('hidden', !isOffline);
        if (isOffline) {
            showToast('Вы оффлайн. Часть действий временно недоступна.', 'info');
        }
    };

    const handleOnline = () => {
        banner.classList.add('hidden');
        showToast('Сеть восстановлена', 'success');
    };

    update();
    window.addEventListener('offline', update);
    window.addEventListener('online', handleOnline);

    return () => {
        window.removeEventListener('offline', update);
        window.removeEventListener('online', handleOnline);
    };
}

function setupPwaInstall(): CleanupFn {
    const button = document.getElementById('pwa-install-btn');
    const iosHint = document.getElementById('pwa-install-ios-hint');
    if (!(button instanceof HTMLElement) || !(iosHint instanceof HTMLElement)) {
        return () => {};
    }

    const isMobile = isMobileViewport();
    const isIos = isIosMobile();
    const isAndroid = isAndroidMobile();

    if (shouldSkipInstallUi(isMobile, isIos, isAndroid)) {
        hideInstallUi(button, iosHint);
        return () => {};
    }

    let deferredPrompt: BeforeInstallPromptEvent | null = null;

    const handleClick = async () => {
        if (isIos) {
            showIosInstructions(iosHint);
            return;
        }

        if (!deferredPrompt) {
            showToast('Откройте меню браузера и выберите "Установить приложение".', 'info');
            return;
        }

        await deferredPrompt.prompt();
        const { outcome } = await deferredPrompt.userChoice;
        if (outcome === 'accepted') {
            hideInstallUi(button, iosHint);
            showToast('Приложение установлено', 'success');
        }
        deferredPrompt = null;
    };

    const handleBeforeInstallPrompt = (event: Event) => {
        const promptEvent = event as BeforeInstallPromptEvent;
        event.preventDefault();
        deferredPrompt = promptEvent;
        button.classList.remove('hidden');
    };

    const handleInstalled = () => hideInstallUi(button, iosHint);

    button.addEventListener('click', handleClick);
    window.addEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
    window.addEventListener('appinstalled', handleInstalled);

    if (isIos) {
        button.classList.remove('hidden');
    }

    return () => {
        button.removeEventListener('click', handleClick);
        window.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
        window.removeEventListener('appinstalled', handleInstalled);
    };
}

function resetIndicator() {
    if (!ptrState.indicator || !ptrState.indicatorText) return;

    ptrState.indicator.classList.remove('active', 'ready', 'loading');
    ptrState.indicator.style.transform = 'translate(-50%, -140%)';
    ptrState.indicatorText.textContent = 'Потяните для обновления';
    ptrState.startY = null;
    ptrState.pullDistance = 0;
    ptrState.pulling = false;
}

function setupPullToRefresh(refreshCallback: RefreshCallback): CleanupFn {
    ptrState.indicator = document.getElementById('pull-refresh-indicator');
    ptrState.indicatorText = document.getElementById('pull-refresh-indicator-text');

    if (!ptrState.indicator || !ptrState.indicatorText) return () => {};
    if (!(('ontouchstart' in window) || navigator.maxTouchPoints > 0) || !isMobileViewport()) return () => {};

    ptrState.refreshCallback = refreshCallback;

    const handleTouchStart = (event: TouchEvent) => {
        if (ptrState.refreshing || (window.scrollY || document.documentElement.scrollTop || 0) > 0) return;
        ptrState.startY = event.touches[0]?.clientY ?? null;
        ptrState.pullDistance = 0;
        ptrState.pulling = true;
    };

    const handleTouchMove = (event: TouchEvent) => {
        if (!ptrState.pulling || ptrState.startY == null || ptrState.refreshing || !ptrState.indicator || !ptrState.indicatorText) {
            return;
        }

        const deltaY = (event.touches[0]?.clientY ?? 0) - ptrState.startY;
        if (deltaY <= 0) {
            resetIndicator();
            return;
        }

        ptrState.pullDistance = Math.min(deltaY, PTR_MAX_PULL);
        const progress = Math.min(ptrState.pullDistance / PTR_TRIGGER_DISTANCE, 1);

        ptrState.indicator.classList.add('active');
        ptrState.indicator.classList.toggle('ready', ptrState.pullDistance >= PTR_TRIGGER_DISTANCE);
        ptrState.indicator.style.transform = `translate(-50%, ${-140 + (progress * 165)}%)`;
        ptrState.indicatorText.textContent = ptrState.pullDistance >= PTR_TRIGGER_DISTANCE
            ? 'Отпустите, чтобы обновить'
            : 'Потяните для обновления';
    };

    const handleTouchEnd = async () => {
        if (!ptrState.pulling || !ptrState.indicator || !ptrState.indicatorText) return;
        ptrState.pulling = false;

        if (ptrState.pullDistance < PTR_TRIGGER_DISTANCE || ptrState.refreshing) {
            resetIndicator();
            return;
        }

        ptrState.refreshing = true;
        ptrState.indicator.classList.add('active', 'loading');
        ptrState.indicator.classList.remove('ready');
        ptrState.indicator.style.transform = 'translate(-50%, 0%)';
        ptrState.indicatorText.textContent = 'Обновляем...';

        try {
            await ptrState.refreshCallback?.();
        } finally {
            ptrState.refreshing = false;
            resetIndicator();
        }
    };

    document.addEventListener('touchstart', handleTouchStart, { passive: true });
    document.addEventListener('touchmove', handleTouchMove, { passive: true });
    document.addEventListener('touchend', handleTouchEnd, { passive: true });

    return () => {
        document.removeEventListener('touchstart', handleTouchStart);
        document.removeEventListener('touchmove', handleTouchMove);
        document.removeEventListener('touchend', handleTouchEnd);
    };
}

function requestImmediateActivation(registration: ServiceWorkerRegistration) {
    if (!registration.waiting) return;
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

function setupServiceWorkerUpdateChecks(registration: ServiceWorkerRegistration) {
    const updateIntervalMs = 5 * 60 * 1000;
    const safeUpdate = () => registration.update().catch((error) => console.log('SW update check failed:', error));

    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') {
            safeUpdate();
        }
    });

    window.addEventListener('focus', safeUpdate);
    window.setInterval(safeUpdate, updateIntervalMs);
}

function setupServiceWorkerLifecycle(registration: ServiceWorkerRegistration) {
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

async function clearLocalhostCaches() {
    if (!('caches' in window)) return;

    try {
        const keys = await caches.keys();
        await Promise.all(keys.map((key) => caches.delete(key)));
    } catch (error) {
        console.log('Cache cleanup failed:', error);
    }
}

async function registerServiceWorker() {
    if (!('serviceWorker' in navigator)) return;
    if (isLocalhost()) {
        await clearLocalhostCaches();
    }
    setupServiceWorkerAutoReload();

    try {
        const registration = await navigator.serviceWorker.register(getServiceWorkerUrl());
        setupServiceWorkerLifecycle(registration);
        setupServiceWorkerUpdateChecks(registration);
    } catch (error) {
        console.log('SW registration failed:', error);
    }
}

export async function initializePwa(refreshCallback: RefreshCallback): Promise<CleanupFn> {
    const cleanupFns = [
        setupOfflineStatusBanner(),
        setupPwaInstall(),
        setupPullToRefresh(refreshCallback),
    ];

    await registerServiceWorker();

    return () => {
        cleanupFns.forEach((cleanup) => cleanup());
    };
}