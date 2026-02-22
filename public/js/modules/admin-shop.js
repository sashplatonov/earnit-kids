import { state } from './state.js';
import { renderShop } from './ui.js';
import { showToast, closeModal, openModal, showConfirm } from './utils.js';
import { scheduleSave } from './actions.js';

let editingShopId = null;

function setShopFields(item) {
    const d = { name: '', group: '', price: '', comment: '', 'money-limit': '', type: 'small', 'freq-limit': 1, 'freq-period': 'week' };
    const f = item || {};
    const m = {
        name: f.name, group: f.group, price: f.price, comment: f.comment,
        'money-limit': f.money_limit, type: f.type,
        'freq-limit': f.frequency?.limit, 'freq-period': f.frequency?.period
    };

    Object.keys(d).forEach(k => {
        const el = document.getElementById(`shop-${k}`);
        if (el) el.value = m[k] ?? d[k];
    });
}

export function openShopModal(itemId = null) {
    editingShopId = itemId;
    const item = itemId ? state.shopItems.find(i => i.id == itemId) : null;
    if (itemId && !item) return;

    const title = document.getElementById('shop-modal-title');
    if (title) title.textContent = itemId ? 'Редактировать товар' : 'Добавить товар';

    setShopFields(item);

    const del = document.getElementById('shop-delete');
    if (del) del.classList.toggle('hidden', !itemId);

    openModal('shop-modal');
}

export function saveShopItem() {
    const name = document.getElementById('shop-name').value.trim();
    const price = parseInt(document.getElementById('shop-price').value);
    if (!name) return showToast('Введите название', 'error');
    if (isNaN(price) || price < 0) return showToast('Введите корректную цену', 'error');

    const fl = parseInt(document.getElementById('shop-freq-limit').value) || 0;
    const data = {
        name, childId: state.currentChildId,
        group: document.getElementById('shop-group').value.trim(),
        price, comment: document.getElementById('shop-comment').value.trim(),
        money_limit: parseInt(document.getElementById('shop-money-limit').value) || null,
        type: document.getElementById('shop-type').value,
        frequency: fl > 0 ? { limit: fl, period: document.getElementById('shop-freq-period').value } : null
    };

    if (editingShopId) {
        const idx = state.shopItems.findIndex(i => i.id == editingShopId);
        if (idx !== -1) state.shopItems[idx] = { ...state.shopItems[idx], id: editingShopId, ...data };
    } else {
        state.shopItems.push({ id: Date.now(), ...data });
    }

    scheduleSave(); renderShop(); closeModal('shop-modal');
    showToast(editingShopId ? 'Товар обновлён!' : 'Товар добавлен!', 'success');
}

export function deleteShopItem() {
    if (!editingShopId) return;
    showConfirm('Удалить товар?', 'Это действие нельзя отменить.', () => {
        state.shopItems = state.shopItems.filter(i => i.id != editingShopId);
        scheduleSave(); renderShop(); closeModal('shop-modal');
        showToast('Товар удалён', 'info');
    });
}

export const editShopItem = (id) => openShopModal(id);
