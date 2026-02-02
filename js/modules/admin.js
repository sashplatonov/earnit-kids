import { state, setState, notify } from './state.js';
import { changePin, saveDataToServer, updateFamilySettingsOnServer } from './api.js';
import { renderTasks, renderShop } from './ui.js';
import { showToast, closeModal, openModal, showConfirm } from './utils.js';
import { scheduleSave } from './actions.js';

let editingTaskId = null;
let editingShopId = null;

// PIN Changing Logic
export function openChangePinModal() {
    document.getElementById('old-pin').value = '';
    document.getElementById('new-pin').value = '';
    openModal('change-pin-modal');
}

export async function saveNewPin() {
    const oldPin = document.getElementById('old-pin').value;
    const newPin = document.getElementById('new-pin').value;

    if (!newPin || newPin.length < 6) {
        showToast('Новый пароль должен быть не менее 6 символов', 'error');
        return;
    }

    const result = await changePin(oldPin, newPin, state.role);

    if (result.success) {
        showToast('Пароль успешно изменен', 'success');
        closeModal('change-pin-modal');
    } else {
        showToast(result.error || 'Ошибка при смене пароля', 'error');
    }
}

// Task Editing
export function openTaskModal(taskId = null) {
    editingTaskId = taskId;
    const title = document.getElementById('task-modal-title');
    const deleteBtn = document.getElementById('task-delete');

    if (taskId) {
        const task = state.tasks.find(t => t.id === taskId);
        if (!task) return;

        if (title) title.textContent = 'Редактировать задание';
        document.getElementById('task-name').value = task.name;
        document.getElementById('task-coins').value = task.coins;
        document.getElementById('task-comment').value = task.comment || '';

        if (task.frequency) {
            document.getElementById('task-freq-limit').value = task.frequency.limit;
            document.getElementById('task-freq-period').value = task.frequency.period;
        } else {
            document.getElementById('task-freq-limit').value = '';
            document.getElementById('task-freq-period').value = 'day';
        }

        if (deleteBtn) deleteBtn.classList.remove('hidden');
    } else {
        if (title) title.textContent = 'Добавить задание';
        document.getElementById('task-name').value = '';
        document.getElementById('task-coins').value = '';
        document.getElementById('task-comment').value = '';
        document.getElementById('task-freq-limit').value = '';
        document.getElementById('task-freq-period').value = 'day';
        if (deleteBtn) deleteBtn.classList.add('hidden');
    }

    openModal('task-modal');
}

export function saveTask() {
    const name = document.getElementById('task-name').value.trim();
    const coins = parseInt(document.getElementById('task-coins').value);
    const comment = document.getElementById('task-comment').value.trim();
    const freqLimit = parseInt(document.getElementById('task-freq-limit').value) || 0;
    const freqPeriod = document.getElementById('task-freq-period').value;

    if (!name) return showToast('Введите название задания', 'error');
    if (!coins || coins < 1) return showToast('Введите количество монет', 'error');

    const taskData = {
        name,
        coins,
        comment,
        frequency: freqLimit > 0 ? { limit: freqLimit, period: freqPeriod } : null
    };

    if (editingTaskId) {
        const index = state.tasks.findIndex(t => t.id === editingTaskId);
        if (index !== -1) {
            state.tasks[index] = { ...state.tasks[index], ...taskData };
        }
    } else {
        state.tasks.push({
            id: Date.now(),
            ...taskData
        });
    }

    scheduleSave();
    renderTasks();
    closeModal('task-modal');
    showToast(editingTaskId ? 'Задание обновлено!' : 'Задание добавлено!', 'success');
}

export function deleteTask() {
    if (!editingTaskId) return;

    showConfirm('Удалить задание?', 'Это действие нельзя отменить.', () => {
        state.tasks = state.tasks.filter(t => t.id !== editingTaskId);
        scheduleSave();
        renderTasks();
        closeModal('task-modal');
        showToast('Задание удалено', 'info');
    });
}

export function editTask(id) {
    openTaskModal(id);
}


// Shop Editing
export function openShopModal(itemId = null) {
    editingShopId = itemId;
    const title = document.getElementById('shop-modal-title');
    const deleteBtn = document.getElementById('shop-delete');

    if (itemId) {
        const item = state.shopItems.find(i => i.id === itemId);
        if (!item) return;

        if (title) title.textContent = 'Редактировать товар';
        document.getElementById('shop-name').value = item.name;
        document.getElementById('shop-price').value = item.price;
        document.getElementById('shop-comment').value = item.comment || '';
        document.getElementById('shop-money-limit').value = item.money_limit || '';
        document.getElementById('shop-type').value = item.type || 'small';

        if (item.frequency) {
            document.getElementById('shop-freq-limit').value = item.frequency.limit;
            document.getElementById('shop-freq-period').value = item.frequency.period;
        } else {
            document.getElementById('shop-freq-limit').value = 1;
            document.getElementById('shop-freq-period').value = 'week';
        }

        if (deleteBtn) deleteBtn.classList.remove('hidden');
    } else {
        if (title) title.textContent = 'Добавить товар';
        document.getElementById('shop-name').value = '';
        document.getElementById('shop-price').value = '';
        document.getElementById('shop-comment').value = '';
        document.getElementById('shop-money-limit').value = '';
        document.getElementById('shop-type').value = 'small';
        document.getElementById('shop-freq-limit').value = 1;
        document.getElementById('shop-freq-period').value = 'week';

        if (deleteBtn) deleteBtn.classList.add('hidden');
    }

    openModal('shop-modal');
}

export function saveShopItem() {
    const name = document.getElementById('shop-name').value.trim();
    const price = parseInt(document.getElementById('shop-price').value);
    const comment = document.getElementById('shop-comment').value.trim();
    const moneyLimit = parseInt(document.getElementById('shop-money-limit').value) || null;
    const type = document.getElementById('shop-type').value;
    const freqLimit = parseInt(document.getElementById('shop-freq-limit').value) || 0;
    const freqPeriod = document.getElementById('shop-freq-period').value;

    if (!name) return showToast('Введите название', 'error');
    if (!price || price < 1) return showToast('Введите цену', 'error');

    const newItem = {
        name,
        price,
        comment,
        money_limit: moneyLimit,
        type,
        frequency: freqLimit > 0 ? { limit: freqLimit, period: freqPeriod } : null
    };

    if (editingShopId) {
        const index = state.shopItems.findIndex(i => i.id === editingShopId);
        if (index !== -1) {
            state.shopItems[index] = { ...state.shopItems[index], id: editingShopId, ...newItem };
        }
    } else {
        state.shopItems.push({
            id: Date.now(),
            ...newItem
        });
    }

    scheduleSave();
    renderShop();
    closeModal('shop-modal');
    showToast(editingShopId ? 'Товар обновлён!' : 'Товар добавлен!', 'success');
}

export function deleteShopItem() {
    if (!editingShopId) return;

    showConfirm('Удалить товар?', 'Это действие нельзя отменить.', () => {
        state.shopItems = state.shopItems.filter(i => i.id !== editingShopId);
        scheduleSave();
        renderShop();
        closeModal('shop-modal');
        showToast('Товар удалён', 'info');
    });
}

export function editShopItem(id) {
    openShopModal(id);
}

export function openFamilySettingsModal() {
    document.getElementById('settings-family-name').value = state.familyName || '';
    document.getElementById('settings-monthly-limit').value = state.monthlyLimit || 2000;
    openModal('family-settings-modal');
}

export async function saveFamilySettings() {
    const name = document.getElementById('settings-family-name').value.trim();
    const monthlyLimit = parseInt(document.getElementById('settings-monthly-limit').value);

    if (!name) {
        showToast('Название не может быть пустым', 'error');
        return;
    }

    const result = await updateFamilySettingsOnServer({ name, monthly_limit: monthlyLimit });
    if (result && result.success) {
        setState({ familyName: name, monthlyLimit: monthlyLimit });
        showToast('Настройки обновлены!', 'success');
        closeModal('family-settings-modal');
    } else {
        showToast('Ошибка при обновлении настроек', 'error');
    }
}
