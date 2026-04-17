/** @file Admin Passwords frontend UI module */
import { state } from './state.js';
import { changePassword } from './api.js';
import { showToast, closeModal, openModal } from './utils.js';

export function openChangePasswordModal() {
    document.getElementById('old-password').value = '';
    document.getElementById('new-password').value = '';
    openModal('change-password-modal');
}

export async function saveNewPassword() {
    const oldPassword = document.getElementById('old-password').value;
    const newPassword = document.getElementById('new-password').value;

    if (!newPassword || newPassword.length < 6) return showToast('Новый пароль должен быть не менее 6 символов', 'error');

    const result = await changePassword(oldPassword, newPassword);
    if (result.success) {
        showToast('Пароль успешно изменен', 'success');
        closeModal('change-password-modal');
    } else {
        showToast(result.error || 'Ошибка при смене пароля', 'error');
    }
}

export async function saveNewPasswordInline() {
    const oldPassword = document.getElementById('settings-old-password-inline').value;
    const newPassword = document.getElementById('settings-new-password-inline').value;

    if (!newPassword || newPassword.length < 6) return showToast('Новый пароль должен быть не менее 6 символов', 'error');

    const result = await changePassword(oldPassword, newPassword);
    if (result.success) {
        showToast('Пароль успешно изменен', 'success');
        document.getElementById('settings-old-password-inline').value = '';
        document.getElementById('settings-new-password-inline').value = '';
    } else {
        showToast(result.error || 'Ошибка при смене пароля', 'error');
    }
}

// Legacy 'pin' aliases removed — use password APIs only
