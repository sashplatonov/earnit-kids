/** @file Mobile PWA install CTA and iOS instructions */
import { showToast } from './utils.js';

const MOBILE_QUERY = '(max-width: 900px)';

function isMobileViewport() {
    return typeof window !== 'undefined'
        && typeof window.matchMedia === 'function'
        && window.matchMedia(MOBILE_QUERY).matches;
}

function getUa() {
    return (navigator.userAgent || '').toLowerCase();
}

function isIosMobile() {
    const ua = getUa();
    return /iphone|ipad|ipod/.test(ua);
}

function isAndroidMobile() {
    return /android/.test(getUa());
}

function isStandaloneMode() {
    const isMediaStandalone = typeof window.matchMedia === 'function'
        && window.matchMedia('(display-mode: standalone)').matches;
    return isMediaStandalone || window.navigator.standalone === true;
}

function hideInstallUi(button, iosHint) {
    button.classList.add('hidden');
    iosHint.classList.add('hidden');
    iosHint.textContent = '';
}

function showIosInstructions(iosHint) {
    iosHint.textContent = 'iOS: Поделиться -> На экран Домой.';
    iosHint.classList.remove('hidden');
}

function shouldSkipInstallUi(isMobile, isIos, isAndroid) {
    return !isMobile || (!isIos && !isAndroid) || isStandaloneMode();
}

function bindInstallEvents(button, iosHint, isIos) {
    let deferredPrompt = null;

    button.addEventListener('click', async () => {
        if (isIos) return showIosInstructions(iosHint);

        if (!deferredPrompt) {
            showToast('Откройте меню браузера и выберите "Установить приложение".', 'info');
            return;
        }

        deferredPrompt.prompt();
        const { outcome } = await deferredPrompt.userChoice;
        if (outcome === 'accepted') {
            hideInstallUi(button, iosHint);
            showToast('Приложение установлено', 'success');
        }
        deferredPrompt = null;
    });

    window.addEventListener('beforeinstallprompt', (event) => {
        event.preventDefault();
        deferredPrompt = event;
        button.classList.remove('hidden');
    });
}

export function setupPwaInstall() {
    const button = document.getElementById('pwa-install-btn');
    const iosHint = document.getElementById('pwa-install-ios-hint');
    if (!button || !iosHint) return;

    const isMobile = isMobileViewport();
    const isIos = isIosMobile();
    const isAndroid = isAndroidMobile();

    if (shouldSkipInstallUi(isMobile, isIos, isAndroid)) return hideInstallUi(button, iosHint);
    bindInstallEvents(button, iosHint, isIos);
    window.addEventListener('appinstalled', () => hideInstallUi(button, iosHint));
    if (isIos) button.classList.remove('hidden');
}
