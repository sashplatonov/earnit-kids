/** @file Admin Shop frontend UI module */
import { state } from './state.js';
import { renderShop } from './ui.js';
import { showToast, closeModal, openModal, showConfirm } from './utils.js';
import { scheduleSave } from './actions.js';

let editingShopId = null;

function getEditingShopItem() {
    return editingShopId ? state.shopItems.find(i => i.id == editingShopId) : null;
}

function getShopValidationError(name, price) {
    if (!name) return 'Введите название';
    if (isNaN(price) || price < 0) return 'Введите корректную цену';
    return null;
}

function buildShopPayload() {
    const fl = parseInt(document.getElementById('shop-freq-limit').value) || 0;
    const existingItem = getEditingShopItem();

    return {
        name: document.getElementById('shop-name').value.trim(),
        childId: existingItem?.childId ?? state.currentChildId,
        groupName: document.getElementById('shop-group').value.trim(),
        price: parseInt(document.getElementById('shop-price').value),
        comment: document.getElementById('shop-comment').value.trim(),
        moneyLimit: parseInt(document.getElementById('shop-money-limit').value) || null,
        type: document.getElementById('shop-type').value,
        frequency: fl > 0 ? { limit: fl, period: document.getElementById('shop-freq-period').value } : null
    };
}

function persistShopItem(data) {
    if (editingShopId) {
        const idx = state.shopItems.findIndex(i => i.id == editingShopId);
        if (idx !== -1) state.shopItems[idx] = { ...state.shopItems[idx], id: editingShopId, ...data };
        return;
    }

    state.shopItems.push({ id: Date.now(), ...data });
}

function setShopFields(item) {
    const d = { name: '', group: '', price: '', comment: '', 'money-limit': '', type: 'small', 'freq-limit': 1, 'freq-period': 'week' };
    const f = item || {};
    const m = {
        name: f.name, group: f.groupName, price: f.price, comment: f.comment,
        'money-limit': f.moneyLimit, type: f.type,
        'freq-limit': f.frequency?.limit, 'freq-period': f.frequency?.period
    };

    Object.keys(d).forEach(k => {
        const el = document.getElementById(`shop-${k}`);
        if (el) el.value = m[k] ?? d[k];
    });
}

export function openShopModal(itemId = null) {
    if (itemId && typeof itemId === 'object') itemId = null;
    editingShopId = itemId;
    const item = getEditingShopItem();
    if (itemId && !item) return;

    const title = document.getElementById('shop-modal-title');
    if (title) title.textContent = itemId ? 'Редактировать товар' : 'Добавить товар';

    setShopFields(item);

    const del = document.getElementById('shop-delete');
    if (del) del.classList.toggle('hidden', !itemId);

    openModal('shop-modal');
}

export function saveShopItem() {
    const data = buildShopPayload();
    const error = getShopValidationError(data.name, data.price);
    if (error) return showToast(error, 'error');

    persistShopItem(data);

    scheduleSave(); renderShop(); closeModal('shop-modal');
    showToast(editingShopId ? 'Товар обновлён!' : 'Товар добавлен!', 'success');
}

export function deleteShopItem() {
    if (!editingShopId) return;
    showConfirm('Удалить товар?', 'Это действие нельзя отменить.', {
        onConfirm: () => {
            const i = state.shopItems.find(item => item.id == editingShopId);
            if (i) i.isDeleted = true;
            scheduleSave(); renderShop(); closeModal('shop-modal');
            showToast('Товар удалён', 'info');
        }
    });
}

export const editShopItem = (id) => openShopModal(id);
