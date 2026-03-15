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

export function autoShrinkCardTitles(container) {
    if (!container) return;
    // Request animation frame allows layout to be painted first
    // so we can read real scrollHeight/clientHeight reliably.
    FRAME_SCHEDULER(() => {
        const titles = container.querySelectorAll('.card__title:not([data-fitted="true"])');
        titles.forEach(el => {
            el.dataset.fitted = 'true'; // Mark to prevent redundant processing
            
            // Revert any previously applied custom inline scaling logic
            el.style.fontSize = '';
            el.style.lineHeight = '';

            let currentSize = 16;
            
            // For card titles, we now strictly fit them into one line without truncation
            // We detect horizontal overflow using scrollWidth vs clientWidth
            while (el.scrollWidth > el.clientWidth && currentSize > 9) {
                currentSize -= 0.5;
                el.style.fontSize = `${currentSize}px`;
                el.style.lineHeight = '1.1';
            }
            // After fitting as much as possible, allow wrapping if it's still slightly over
            // but the detection worked on the 'nowrap' state from CSS.
            el.style.whiteSpace = 'normal';
            el.style.wordBreak = 'break-word';
        });
    });
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
        autoShrinkCardTitles(container);
        return;
    }

    function renderNext() {
        if (chunkRenderJobs.get(container) !== job) return;
        const start = job.index;
        const end = Math.min(parts.length, start + chunkSize);
        container.insertAdjacentHTML('beforeend', parts.slice(start, end).join(''));
        job.index = end;
        autoShrinkCardTitles(container);
        
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
        // Close only when the native dialog backdrop itself is clicked.
        // Coordinate-based checks misfire on mobile/select pickers and close the modal mid-edit.
        if (!modal._backdropListener) {
            modal._backdropListener = (e) => {
                if (e.target === modal) {
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

export function showConfirm(title, message, options = {}) {
    document.getElementById('confirm-title').innerHTML = title;
    document.getElementById('confirm-message').innerHTML = message;
    
    const okBtn = document.getElementById('confirm-ok');
    const cancelBtn = document.getElementById('confirm-cancel');
    
    if (okBtn) {
        okBtn.textContent = options.confirmLabel || 'Подтвердить';
        okBtn.classList.toggle('hidden', !!options.hideConfirm);
    }
    
    if (cancelBtn) {
        cancelBtn.textContent = options.cancelLabel || 'Отмена';
    }
    
    confirmCallback = options.onConfirm || null;
    openModal('confirm-modal');
}

export function handleConfirm() {
    if (confirmCallback) {
        const cb = confirmCallback;
        confirmCallback = null;
        cb();
    }
    closeModal('confirm-modal');
    
    // Reset buttons after closing
    setTimeout(() => {
        const okBtn = document.getElementById('confirm-ok');
        const cancelBtn = document.getElementById('confirm-cancel');
        if (okBtn) {
            okBtn.textContent = 'Подтвердить';
            okBtn.classList.remove('hidden');
        }
        if (cancelBtn) cancelBtn.textContent = 'Отмена';
    }, 300);
}
