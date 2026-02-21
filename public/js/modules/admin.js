import { state, setState, notify } from './state.js';
import { changePin, saveDataToServer, updateFamilySettingsOnServer, addChild, getChildLink, regenerateChildToken, updateChildSettings } from './api.js';
import { renderTasks, renderShop, renderAll } from './ui.js';
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

export async function saveNewPinInline() {
    const oldPin = document.getElementById('settings-old-pin-inline').value;
    const newPin = document.getElementById('settings-new-pin-inline').value;

    if (!newPin || newPin.length < 6) {
        showToast('Новый пароль должен быть не менее 6 символов', 'error');
        return;
    }

    const result = await changePin(oldPin, newPin, state.role);

    if (result.success) {
        showToast('Пароль успешно изменен', 'success');
        document.getElementById('settings-old-pin-inline').value = '';
        document.getElementById('settings-new-pin-inline').value = '';
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
        const task = state.tasks.find(t => t.id == taskId);
        if (!task) return;

        if (title) title.textContent = 'Редактировать задание';
        document.getElementById('task-name').value = task.name;
        document.getElementById('task-group').value = task.group || '';
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
        document.getElementById('task-group').value = '';
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
        childId: state.currentChildId, // Assign to current child
        group: document.getElementById('task-group').value.trim(),
        coins,
        comment,
        frequency: freqLimit > 0 ? { limit: freqLimit, period: freqPeriod } : null
    };

    if (editingTaskId) {
        const index = state.tasks.findIndex(t => t.id == editingTaskId);
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
        state.tasks = state.tasks.filter(t => t.id != editingTaskId);
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
        const item = state.shopItems.find(i => i.id == itemId);
        if (!item) return;

        if (title) title.textContent = 'Редактировать товар';
        document.getElementById('shop-name').value = item.name;
        document.getElementById('shop-group').value = item.group || '';
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
        document.getElementById('shop-group').value = '';
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
    if (isNaN(price) || price < 0) return showToast('Введите корректную цену', 'error');

    const newItem = {
        name,
        childId: state.currentChildId, // Assign to current child
        group: document.getElementById('shop-group').value.trim(),
        price,
        comment,
        money_limit: moneyLimit,
        type,
        frequency: freqLimit > 0 ? { limit: freqLimit, period: freqPeriod } : null
    };

    if (editingShopId) {
        const index = state.shopItems.findIndex(i => i.id == editingShopId);
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
        state.shopItems = state.shopItems.filter(i => i.id != editingShopId);
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
    document.getElementById('settings-money-limit').value = state.monthlyLimit || 10000;
    openModal('family-settings-modal');
}

export async function saveFamilySettings() {
    const name = document.getElementById('settings-family-name').value.trim();
    const monthlyLimit = parseInt(document.getElementById('settings-money-limit').value);

    if (!name) {
        showToast('Название не может быть пустым', 'error');
        return;
    }

    const result = await updateFamilySettingsOnServer({ name, monthly_limit: monthlyLimit });
    if (result && result.success) {
        setState({ familyName: name });
        showToast('Настройки обновлены!', 'success');
        closeModal('family-settings-modal');
    } else {
        showToast('Ошибка при обновлении настроек', 'error');
    }
}

export async function saveFamilySettingsInline() {
    const name = document.getElementById('settings-family-name-inline').value.trim();
    // const monthlyLimit = parseInt(document.getElementById('settings-money-limit-inline').value); // Use child limit now

    if (!name) {
        showToast('Название не может быть пустым', 'error');
        return;
    }

    // Update Family Name
    const familyRes = await updateFamilySettingsOnServer({ name });
    if (familyRes && familyRes.success) {
        setState({ familyName: name });
    } else {
        return showToast('Ошибка при обновлении названия семьи', 'error');
    }

    // If child selected, update child settings
    if (state.currentChildId) {
        const childName = document.getElementById('settings-child-name-inline')?.value.trim();
        const mLimit = parseInt(document.getElementById('settings-money-limit-inline')?.value);
        const dayLimit = parseInt(document.getElementById('settings-day-coin-limit-inline')?.value);

        const childRes = await updateChildSettings(state.familyId, state.currentChildId, {
            name: childName,
            monthly_limit: isNaN(mLimit) ? 0 : mLimit,
            daily_coin_limit: isNaN(dayLimit) ? 0 : dayLimit
        });

        if (childRes.success) {
            // Update state
            const childIndex = state.children.findIndex(c => c.id == state.currentChildId);
            if (childIndex !== -1) {
                state.children[childIndex].name = childName;
                state.children[childIndex].monthlyLimit = isNaN(mLimit) ? 0 : mLimit;
                state.children[childIndex].dailyCoinLimit = isNaN(dayLimit) ? 0 : dayLimit;
                renderChildSwitcher(); // Update name in tabs
            }
        } else {
            return showToast('Ошибка при обновлении настроек ребенка', 'error');
        }
    }

    showToast('Настройки обновлены!', 'success');
    renderAll();
}

export async function refreshChildLinkInline() {
    const input = document.getElementById('settings-child-link-input-inline');
    if (!input) return;

    // If we have a current child selected, get THEIR link.
    // Otherwise, maybe disable or show generic msg?
    const childId = state.currentChildId;
    // Default to first child if none selected? Or just fail?
    // Let's assume parent settings allows selecting child for link in future.
    // For now, if currentChildId is set, use it. If not, maybe use first.

    const targetId = childId || (state.children.length > 0 ? state.children[0].id : null);
    if (!targetId) {
        input.value = 'Сначала добавьте ребенка';
        return;
    }

    try {
        const data = await getChildLink(targetId);
        if (data.link) {
            input.value = data.link;
        }
    } catch (err) {
        console.error('Error fetching child link:', err);
    }
}

export async function copyChildLinkInline() {
    const input = document.getElementById('settings-child-link-input-inline');
    if (input && input.value) {
        input.select();
        try {
            document.execCommand('copy');
            showToast('Ссылка скопирована!', 'success');
            const status = document.getElementById('child-link-status');
            if (status) {
                status.classList.remove('hidden');
                setTimeout(() => status.classList.add('hidden'), 3000);
            }
        } catch (err) {
            showToast('Не удалось скопировать', 'error');
        }
    }
}

export async function regenerateChildLinkInline() {
    if (!confirm('Вы уверены, что хотите обновить ссылку? Старая ссылка перестанет работать.')) return;

    const childId = state.currentChildId || (state.children.length > 0 ? state.children[0].id : null);
    if (!childId) return showToast('Нет выбранного ребенка', 'error');

    try {
        const data = await regenerateChildToken(childId);
        if (data.link) {
            const input = document.getElementById('settings-child-link-input-inline');
            if (input) input.value = data.link;
            showToast('Ссылка обновлена', 'success');
        } else {
            showToast('Ошибка при обновлении ссылки', 'error');
        }
    } catch (err) {
        showToast('Ошибка сети', 'error');
    }
}
export function switchChild(childId) {
    const child = state.children.find(c => c.id == childId);
    if (child) {
        setState({
            currentChildId: childId,
            balance: child.balance || 0,
            monthlyLimit: child.monthlyLimit !== undefined ? child.monthlyLimit : 10000,
            dailyCoinLimit: child.dailyCoinLimit !== undefined ? child.dailyCoinLimit : 0
        });
    } else {
        setState({ currentChildId: childId });
    }
    renderAll();

    // Refresh analytics if on that tab
    const analyticsSection = document.getElementById('analytics-section');
    if (analyticsSection && !analyticsSection.classList.contains('hidden')) {
        import('./analytics-ui.js').then(m => m.loadAnalytics());
    }

    // Update settings fields if they exist
    if (child) {
        const nameInp = document.getElementById('settings-child-name-inline');
        if (nameInp) nameInp.value = child.name;

        const limitInp = document.getElementById('settings-money-limit-inline');
        if (limitInp) limitInp.value = child.monthlyLimit !== undefined ? child.monthlyLimit : 10000;

        const dayLimitInp = document.getElementById('settings-day-coin-limit-inline');
        if (dayLimitInp) dayLimitInp.value = child.dailyCoinLimit !== undefined ? child.dailyCoinLimit : 0;

        refreshChildLinkInline();
    }
}

export function openAddChildModal() {
    document.getElementById('new-child-name').value = '';
    openModal('add-child-modal');
}

export async function saveNewChild() {
    const name = document.getElementById('new-child-name').value.trim();
    if (!name) return showToast('Введите имя', 'error');

    const result = await addChild(name);
    if (result.success) {
        showToast('Ребенок добавлен!', 'success');
        closeModal('add-child-modal');
        // Reload page or just reload data?
        // Data reload is better but full reload ensures state consistency for now
        window.location.reload();
    } else {
        showToast('Ошибка: ' + (result.error || 'Не удалось добавить'), 'error');
    }
}
