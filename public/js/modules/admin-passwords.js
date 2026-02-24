/** @file Admin Passwords frontend UI module */
import { state } from './state.js';
import { changePin } from './api.js';
import { showToast, closeModal, openModal } from './utils.js';

export function openChangePinModal() {
    document.getElementById('old-pin').value = '';
    document.getElementById('new-pin').value = '';
    openModal('change-pin-modal');
}

export async function saveNewPin() {
    const oldPin = document.getElementById('old-pin').value;
    const newPin = document.getElementById('new-pin').value;

    if (!newPin || newPin.length < 6) return showToast('Новый пароль должен быть не менее 6 символов', 'error');

    const result = await changePin(oldPin, newPin, state.role);
    if (result.success) {
        showToast('Пароль успешно изменен', 'success');
        closeModal('change-pin-modal');
    } else {
        showToast(result.error || 'Ошибка при смене пароля', 'error');
    }
}

export async function saveNewPinInline() {
    const oldPin = document.getElementById('settings-old-pin-inline').value;
    const newPin = document.getElementById('settings-new-pin-inline').value;

    if (!newPin || newPin.length < 6) return showToast('Новый пароль должен быть не менее 6 символов', 'error');

    const result = await changePin(oldPin, newPin, state.role);
    if (result.success) {
        showToast('Пароль успешно изменен', 'success');
        document.getElementById('settings-old-pin-inline').value = '';
        document.getElementById('settings-new-pin-inline').value = '';
    } else {
        showToast(result.error || 'Ошибка при смене пароля', 'error');
    }
}
