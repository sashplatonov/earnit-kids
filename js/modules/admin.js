import { state, setState, notify } from './state.js';
import { login, saveDataToServer } from './api.js';
import { updateAdminUI, renderTasks, renderShop, renderAll } from './ui.js';
import { showToast, closeModal, openModal, showConfirm } from './utils.js';
import { scheduleSave } from './actions.js';

let editingTaskId = null;
let editingShopId = null;

// Admin Toggle Logic
export function toggleAdminMode() {
    if (state.isAdmin) {
        // Logout
        setState({ isAdmin: false });
        updateAdminUI();
        showToast('Вы вышли из режима администратора', 'info');
    } else {
        // Login
        openModal('pin-modal');
        const input = document.getElementById('pin-input');
        if (input) {
            input.value = '';
            input.focus();
        }

        // Hint logic
        const hint = document.getElementById('pin-hint');
        const title = document.getElementById('pin-modal-title');

        if (state.isPinSet) {
            if (hint) hint.classList.add('hidden');
            if (title) title.textContent = 'Введите PIN';
        } else {
            if (hint) {
                hint.classList.remove('hidden');
                hint.textContent = 'Придумайте PIN-код для входа';
            }
            if (title) title.textContent = 'Создание PIN';
        }
    }
}

export async function checkPin() {
    const input = document.getElementById('pin-input').value;
    if (!input || input.length < 4) {
        showToast('PIN должен быть минимум 4 символа', 'error');
        return;
    }

    if (!state.isPinSet) {
        const success = await saveDataToServer({
            pin: input,
            balance: state.balance,
            tasks: state.tasks,
            shop: state.shopItems,
            history: state.history,
            requests: state.requests
        });

        if (success) {
            setState({ isAdmin: true, isPinSet: true });
            closeModal('pin-modal');
            updateAdminUI();
            showToast('PIN сохранён! Вы вошли как администратор', 'success');
        } else {
            showToast('Ошибка сохранения PIN', 'error');
        }
        return;
    }

    // Server-side validation
    const result = await login(input);

    if (result.success) {
        setState({ isAdmin: true });
        closeModal('pin-modal');
        updateAdminUI();
        showToast('Добро пожаловать, администратор!', 'success');
    } else if (result.status === 429) {
        showToast(result.error || 'Слишком много попыток', 'error');
    } else {
        showToast('Неверный PIN-код', 'error');
        const el = document.getElementById('pin-input');
        if (el) {
            el.value = '';
            el.focus();
        }
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
        const newState = { ...state };
        newState.tasks = newState.tasks.filter(t => t.id !== editingTaskId);
        setState(newState); // Triggers notify if we used direct assignment in `tasks` logic? 
        // We modified array in place in saveTask, here we replace. 
        // Consistency: best to manipulate state object and call setState or rely on ref references.
        // Since `state` is exported as const ref to obj, modifying properties works if observers read them.
        // `notify()` is key.

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
        document.getElementById('shop-rsd').value = item.rsdLimit || '';
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
        document.getElementById('shop-rsd').value = '';
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
    const rsdLimit = parseInt(document.getElementById('shop-rsd').value) || 0;
    const type = document.getElementById('shop-type').value;
    const freqLimit = parseInt(document.getElementById('shop-freq-limit').value) || 0;
    const freqPeriod = document.getElementById('shop-freq-period').value;

    if (!name) return showToast('Введите название', 'error');
    if (!price || price < 1) return showToast('Введите цену', 'error');

    const newItem = {
        name,
        price,
        comment,
        rsdLimit,
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
