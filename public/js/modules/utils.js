/** @file Utils frontend UI module */
export function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

const FRAME_SCHEDULER = typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function'
    ? window.requestAnimationFrame.bind(window)
    : (cb) => setTimeout(cb, 16);

const chunkRenderJobs = new WeakMap();
const DEFAULT_CHUNK_SIZE = 6;

export function isMobileViewport() {
    return typeof window !== 'undefined'
        && typeof window.matchMedia === 'function'
        && window.matchMedia('(max-width: 900px)').matches;
}

export function chunkedRender(container, fragments, options = {}) {
    if (!container) return;

    const parts = Array.isArray(fragments)
        ? fragments.filter(Boolean)
        : (fragments ? [fragments] : []);

    if (!parts.length) {
        container.innerHTML = '';
        return;
    }

    const chunkSize = Math.max(1, options.chunkSize ?? DEFAULT_CHUNK_SIZE);
    const job = { id: Symbol('chunked'), index: 0 };
    chunkRenderJobs.set(container, job);
    container.innerHTML = '';

    if (parts.length <= chunkSize) {
        container.innerHTML = parts.join('');
        chunkRenderJobs.delete(container);
        return;
    }

    function renderNext() {
        if (chunkRenderJobs.get(container) !== job) return;
        const start = job.index;
        const end = Math.min(parts.length, start + chunkSize);
        container.insertAdjacentHTML('beforeend', parts.slice(start, end).join(''));
        job.index = end;
        if (job.index < parts.length) {
            FRAME_SCHEDULER(renderNext);
        } else {
            chunkRenderJobs.delete(container);
        }
    }

    FRAME_SCHEDULER(renderNext);
}

export function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;
    // Using global CONFIG if available, or fallbacks
    const icons = {
        success: '✅',
        error: '❌',
        info: 'ℹ️'
    };

    const toast = document.createElement('div');
    toast.className = `toast toast--${type}`;
    toast.innerHTML = `
        <span class="toast__icon">${icons[type]}</span>
        <span class="toast__message">${escapeHtml(message)}</span>
    `;

    container.appendChild(toast);

    setTimeout(() => {
        toast.classList.add('hiding');
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

let mobilePermissionChecked = false;
let webNotificationPermissionChecked = false;

function getLocalNotificationsPlugin() {
    if (typeof window === 'undefined' || !window.Capacitor) return null;

    const isNative = typeof window.Capacitor.isNativePlatform === 'function'
        && window.Capacitor.isNativePlatform();
    if (!isNative) return null;

    return window.Capacitor.Plugins?.LocalNotifications || null;
}

async function tryShowNativeMobileNotification(title, message) {
    const localNotifications = getLocalNotificationsPlugin();
    if (!localNotifications) return;

    try {
        if (!mobilePermissionChecked) {
            const permissions = await localNotifications.checkPermissions();
            if (permissions.display !== 'granted') {
                await localNotifications.requestPermissions();
            }
            mobilePermissionChecked = true;
        }

        const id = Date.now() % 2147483647;
        await localNotifications.schedule({
            notifications: [{
                id,
                title,
                body: message,
                schedule: { at: new Date(Date.now() + 150) }
            }]
        });
    } catch (err) {
        console.warn('Mobile notification failed:', err);
    }
}

async function tryShowWebNotification(title, message) {
    if (typeof window === 'undefined' || typeof window.Notification === 'undefined') return;

    try {
        if (!webNotificationPermissionChecked) {
            if (window.Notification.permission !== 'granted') {
                await window.Notification.requestPermission();
            }
            webNotificationPermissionChecked = true;
        }

        if (window.Notification.permission === 'granted') {
            new window.Notification(title, { body: message });
        }
    } catch (err) {
        console.warn('Web notification failed:', err);
    }
}

export function showMobileEventNotification(message, type = 'info', title = 'EarnIt Kids') {
    showToast(message, type);

    const localNotifications = getLocalNotificationsPlugin();
    if (!isMobileViewport() && !localNotifications) {
        return;
    }

    if (localNotifications) {
        void tryShowNativeMobileNotification(title, message);
        return;
    }

    void tryShowWebNotification(title, message);
}

export function openModal(modalId) {
    const modal = document.getElementById(modalId);
    if (!modal) return;

    if (modal.tagName === 'DIALOG') {
        modal.showModal();
        // Close on backdrop click (click outside modal__content)
        if (!modal._backdropListener) {
            modal._backdropListener = (e) => {
                const rect = modal.querySelector('.modal__content')?.getBoundingClientRect();
                if (rect && (e.clientX < rect.left || e.clientX > rect.right ||
                    e.clientY < rect.top || e.clientY > rect.bottom)) {
                    modal.close();
                }
            };
            modal.addEventListener('click', modal._backdropListener);
        }
    } else {
        modal.classList.add('active');
    }

    const firstInput = modal.querySelector('input:not([type="hidden"]), textarea');
    if (firstInput) {
        setTimeout(() => firstInput.focus(), 100);
    }
}

export function closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (!modal) return;

    if (modal.tagName === 'DIALOG') {
        modal.close();
    } else {
        modal.classList.remove('active');
    }
}

let confirmCallback = null;

export function showConfirm(title, message, callback) {
    document.getElementById('confirm-title').textContent = title;
    document.getElementById('confirm-message').textContent = message;
    confirmCallback = callback;
    openModal('confirm-modal');
}

export function handleConfirm() {
    if (confirmCallback) {
        confirmCallback();
        confirmCallback = null;
    }
    closeModal('confirm-modal');
}
