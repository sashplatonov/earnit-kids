/** @file Super-admin modal dialogs for confirmations and alerts */
const modal = document.getElementById('super-confirm-modal');
const titleEl = document.getElementById('super-confirm-title');
const messageEl = document.getElementById('super-confirm-message');
const closeBtn = document.getElementById('super-confirm-close');
const cancelBtn = document.getElementById('super-confirm-cancel');
const okBtn = document.getElementById('super-confirm-ok');

let resolver = null;

function canUseCustomDialog() {
    return Boolean(modal && titleEl && messageEl && okBtn && cancelBtn);
}

function getFallbackMessage({ title, message }) {
    return message || title || 'Подтвердить действие?';
}

function closeDialog(result) {
    if (!modal) return;
    modal.classList.remove('active');
    modal.setAttribute('aria-hidden', 'true');
    if (resolver) {
        resolver(result);
        resolver = null;
    }
}

function showDialog({ title, message, confirmText = 'Подтвердить', showCancel = true }) {
    if (!canUseCustomDialog()) {
        return Promise.resolve(window.confirm(getFallbackMessage({ title, message })));
    }

    titleEl.textContent = title || 'Подтверждение';
    messageEl.textContent = message || '';
    okBtn.textContent = confirmText;
    cancelBtn.hidden = !showCancel;
    modal.classList.add('active');
    modal.setAttribute('aria-hidden', 'false');

    return new Promise((resolve) => {
        resolver = resolve;
    });
}

function bindDialogEvents() {
    if (!modal || modal.dataset.bound === 'true') return;
    modal.dataset.bound = 'true';

    closeBtn?.addEventListener('click', () => closeDialog(false));
    cancelBtn?.addEventListener('click', () => closeDialog(false));
    okBtn?.addEventListener('click', () => closeDialog(true));
    modal.addEventListener('click', (event) => {
        if (event.target === modal) closeDialog(false);
    });
}

export async function showSuperConfirm({ title, message, confirmText = 'Подтвердить' }) {
    bindDialogEvents();
    return showDialog({ title, message, confirmText, showCancel: true });
}

export async function showSuperAlert({ title, message, confirmText = 'ОК' }) {
    bindDialogEvents();
    await showDialog({ title, message, confirmText, showCancel: false });
}
