// ===== Data Management =====
const API_URL = '/api/data';

// State
let isAdmin = false;
let balance = 0;
let tasks = [];
let shopItems = [];
let history = [];
let adminPin = null;
let editingTaskId = null;
let editingShopId = null;
let confirmCallback = null;

// ===== API Functions =====
async function loadDataFromServer() {
    try {
        const response = await fetch(API_URL);
        if (response.ok) {
            const data = await response.json();
            adminPin = data.pin;
            balance = data.balance || 0;
            tasks = data.tasks || [];
            shopItems = data.shop || [];
            history = data.history || [];
            return true;
        }
    } catch (err) {
        console.error('Failed to load from server:', err);
    }
    return false;
}

async function saveDataToServer() {
    try {
        const data = {
            pin: adminPin,
            balance: balance,
            tasks: tasks,
            shop: shopItems,
            history: history
        };
        const response = await fetch(API_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });
        return response.ok;
    } catch (err) {
        console.error('Failed to save to server:', err);
        return false;
    }
}

// Debounced save
let saveTimeout = null;
function scheduleSave() {
    if (saveTimeout) clearTimeout(saveTimeout);
    saveTimeout = setTimeout(() => {
        saveDataToServer();
    }, 500);
}

function addHistoryEntry(type, amount, description) {
    const entry = {
        id: Date.now(),
        type: type, // 'earn' | 'spend'
        amount: amount,
        description: description,
        date: new Date().toISOString()
    };
    history.unshift(entry);
    scheduleSave();
    renderHistory();
}

// ===== UI Rendering =====
function updateBalance() {
    document.getElementById('balance').textContent = balance;
}

function renderTasks() {
    const container = document.getElementById('tasks-list');
    const emptyState = document.getElementById('tasks-empty');

    if (tasks.length === 0) {
        container.innerHTML = '';
        emptyState.classList.remove('hidden');
        return;
    }

    emptyState.classList.add('hidden');
    container.innerHTML = tasks.map(task => `
        <div class="card" data-id="${task.id}">
            <div class="card__header">
                <h3 class="card__title">${escapeHtml(task.name)}</h3>
                <div class="card__coins">
                    <span>${task.coins}</span>
                    <span>🪙</span>
                </div>
            </div>
            ${task.comment ? `<p class="card__comment">${escapeHtml(task.comment)}</p>` : ''}
            <div class="card__actions">
                ${isAdmin ? `
                    <button class="btn btn--success btn--small" onclick="earnCoins(${task.id})">
                        ✓ Начислить
                    </button>
                    <button class="btn btn--secondary btn--small" onclick="editTask(${task.id})">
                        ✏️ Изменить
                    </button>
                ` : ''}
            </div>
        </div>
    `).join('');
}

function renderShop() {
    const container = document.getElementById('shop-list');
    const emptyState = document.getElementById('shop-empty');

    if (shopItems.length === 0) {
        container.innerHTML = '';
        emptyState.classList.remove('hidden');
        return;
    }

    emptyState.classList.add('hidden');
    container.innerHTML = shopItems.map(item => {
        const canAfford = balance >= item.price;
        return `
            <div class="card ${canAfford ? 'card--affordable' : ''}" data-id="${item.id}">
                <div class="card__header">
                    <h3 class="card__title">${escapeHtml(item.name)}</h3>
                    <div class="card__coins">
                        <span>${item.price}</span>
                        <span>🪙</span>
                    </div>
                </div>
                ${item.comment ? `<p class="card__comment">${escapeHtml(item.comment)}</p>` : ''}
                <div class="card__actions">
                    <button class="btn btn--primary btn--small" 
                            onclick="buyItem(${item.id})" 
                            ${!canAfford ? 'disabled style="opacity:0.5;cursor:not-allowed;"' : ''}>
                        🛒 ${canAfford ? 'Купить' : 'Не хватает'}
                    </button>
                    ${isAdmin ? `
                        <button class="btn btn--secondary btn--small" onclick="editShopItem(${item.id})">
                            ✏️ Изменить
                        </button>
                    ` : ''}
                </div>
            </div>
        `;
    }).join('');
}

function renderHistory() {
    const container = document.getElementById('history-list');
    const emptyState = document.getElementById('history-empty');

    if (history.length === 0) {
        container.innerHTML = '';
        emptyState.classList.remove('hidden');
        return;
    }

    emptyState.classList.add('hidden');
    container.innerHTML = history.map(entry => {
        const isEarn = entry.type === 'earn';
        const date = new Date(entry.date);
        const formattedDate = date.toLocaleDateString('ru-RU', {
            day: 'numeric',
            month: 'short',
            hour: '2-digit',
            minute: '2-digit'
        });

        return `
            <div class="history-item history-item--${entry.type}">
                <div class="history-item__icon">${isEarn ? '💰' : '🛍️'}</div>
                <div class="history-item__content">
                    <div class="history-item__desc">${escapeHtml(entry.description)}</div>
                    <div class="history-item__date">${formattedDate}</div>
                </div>
                <div class="history-item__amount">
                    ${isEarn ? '+' : '-'}${entry.amount} 🪙
                </div>
            </div>
        `;
    }).join('');
}

function renderAll() {
    updateBalance();
    renderTasks();
    renderShop();
    renderHistory();
}

// ===== Admin Functions =====
function toggleAdminMode() {
    if (isAdmin) {
        // Выход
        isAdmin = false;
        updateAdminUI();
        showToast('Вы вышли из режима администратора', 'info');
    } else {
        // Вход
        openModal('pin-modal');
        document.getElementById('pin-input').value = '';
        document.getElementById('pin-input').focus();

        // Показываем подсказку только если PIN ещё не установлен
        document.getElementById('pin-hint').classList.toggle('hidden', !!adminPin);
    }
}

function checkPin() {
    const input = document.getElementById('pin-input').value;
    if (!input || input.length < 4) {
        showToast('PIN должен быть минимум 4 символа', 'error');
        return;
    }

    if (!adminPin) {
        // Первый вход - сохраняем PIN
        adminPin = input;
        scheduleSave();
        isAdmin = true;
        closeModal('pin-modal');
        updateAdminUI();
        showToast('PIN сохранён! Вы вошли как администратор', 'success');
    } else if (adminPin === input) {
        // Правильный PIN
        isAdmin = true;
        closeModal('pin-modal');
        updateAdminUI();
        showToast('Добро пожаловать, администратор!', 'success');
    } else {
        // Неправильный PIN
        showToast('Неверный PIN-код', 'error');
        document.getElementById('pin-input').value = '';
        document.getElementById('pin-input').focus();
    }
}

function updateAdminUI() {
    const adminBtn = document.getElementById('admin-toggle');
    adminBtn.classList.toggle('active', isAdmin);
    adminBtn.querySelector('.btn__text').textContent = isAdmin ? 'Выход' : 'Вход админа';

    // Показываем/скрываем админские кнопки
    document.querySelectorAll('.admin-only').forEach(el => {
        el.classList.toggle('hidden', !isAdmin);
    });

    // Перерендериваем карточки для отображения кнопок
    renderTasks();
    renderShop();
}

// ===== Task Functions =====
function openTaskModal(taskId = null) {
    editingTaskId = taskId;
    const modal = document.getElementById('task-modal');
    const title = document.getElementById('task-modal-title');
    const deleteBtn = document.getElementById('task-delete');

    if (taskId) {
        const task = tasks.find(t => t.id === taskId);
        if (!task) return;

        title.textContent = 'Редактировать задание';
        document.getElementById('task-name').value = task.name;
        document.getElementById('task-coins').value = task.coins;
        document.getElementById('task-comment').value = task.comment || '';
        deleteBtn.classList.remove('hidden');
    } else {
        title.textContent = 'Добавить задание';
        document.getElementById('task-name').value = '';
        document.getElementById('task-coins').value = '';
        document.getElementById('task-comment').value = '';
        deleteBtn.classList.add('hidden');
    }

    openModal('task-modal');
}

function saveTask() {
    const name = document.getElementById('task-name').value.trim();
    const coins = parseInt(document.getElementById('task-coins').value);
    const comment = document.getElementById('task-comment').value.trim();

    if (!name) {
        showToast('Введите название задания', 'error');
        return;
    }
    if (!coins || coins < 1) {
        showToast('Введите количество монет', 'error');
        return;
    }

    if (editingTaskId) {
        // Редактирование
        const index = tasks.findIndex(t => t.id === editingTaskId);
        if (index !== -1) {
            tasks[index] = { ...tasks[index], name, coins, comment };
        }
    } else {
        // Создание
        tasks.push({
            id: Date.now(),
            name,
            coins,
            comment
        });
    }

    scheduleSave();
    renderTasks();
    closeModal('task-modal');
    showToast(editingTaskId ? 'Задание обновлено!' : 'Задание добавлено!', 'success');
}

function deleteTask() {
    if (!editingTaskId) return;

    showConfirm('Удалить задание?', 'Это действие нельзя отменить.', () => {
        tasks = tasks.filter(t => t.id !== editingTaskId);
        scheduleSave();
        renderTasks();
        closeModal('task-modal');
        showToast('Задание удалено', 'info');
    });
}

function editTask(id) {
    openTaskModal(id);
}

function earnCoins(taskId) {
    const task = tasks.find(t => t.id === taskId);
    if (!task) return;

    showConfirm(
        'Начислить монеты?',
        `Начислить ${task.coins} 🪙 за "${task.name}"?`,
        () => {
            balance += task.coins;
            updateBalance();
            addHistoryEntry('earn', task.coins, task.name);
            renderShop(); // Обновляем доступность покупок
            showToast(`+${task.coins} 🪙 начислено!`, 'success');
        }
    );
}

// ===== Shop Functions =====
function openShopModal(itemId = null) {
    editingShopId = itemId;
    const title = document.getElementById('shop-modal-title');
    const deleteBtn = document.getElementById('shop-delete');

    if (itemId) {
        const item = shopItems.find(i => i.id === itemId);
        if (!item) return;

        title.textContent = 'Редактировать товар';
        document.getElementById('shop-name').value = item.name;
        document.getElementById('shop-price').value = item.price;
        document.getElementById('shop-comment').value = item.comment || '';
        deleteBtn.classList.remove('hidden');
    } else {
        title.textContent = 'Добавить товар';
        document.getElementById('shop-name').value = '';
        document.getElementById('shop-price').value = '';
        document.getElementById('shop-comment').value = '';
        deleteBtn.classList.add('hidden');
    }

    openModal('shop-modal');
}

function saveShopItem() {
    const name = document.getElementById('shop-name').value.trim();
    const price = parseInt(document.getElementById('shop-price').value);
    const comment = document.getElementById('shop-comment').value.trim();

    if (!name) {
        showToast('Введите название товара', 'error');
        return;
    }
    if (!price || price < 1) {
        showToast('Введите цену', 'error');
        return;
    }

    if (editingShopId) {
        const index = shopItems.findIndex(i => i.id === editingShopId);
        if (index !== -1) {
            shopItems[index] = { ...shopItems[index], name, price, comment };
        }
    } else {
        shopItems.push({
            id: Date.now(),
            name,
            price,
            comment
        });
    }

    scheduleSave();
    renderShop();
    closeModal('shop-modal');
    showToast(editingShopId ? 'Товар обновлён!' : 'Товар добавлен!', 'success');
}

function deleteShopItem() {
    if (!editingShopId) return;

    showConfirm('Удалить товар?', 'Это действие нельзя отменить.', () => {
        shopItems = shopItems.filter(i => i.id !== editingShopId);
        scheduleSave();
        renderShop();
        closeModal('shop-modal');
        showToast('Товар удалён', 'info');
    });
}

function editShopItem(id) {
    openShopModal(id);
}

function buyItem(itemId) {
    const item = shopItems.find(i => i.id === itemId);
    if (!item) return;

    if (balance < item.price) {
        showToast('Недостаточно монет!', 'error');
        return;
    }

    showConfirm(
        'Подтвердите покупку',
        `Купить "${item.name}" за ${item.price} 🪙?`,
        () => {
            balance -= item.price;
            updateBalance();
            addHistoryEntry('spend', item.price, item.name);
            renderShop();
            showToast(`Вы купили: ${item.name}!`, 'success');
        }
    );
}

// ===== Modal Functions =====
function openModal(modalId) {
    document.getElementById(modalId).classList.add('active');
}

function closeModal(modalId) {
    document.getElementById(modalId).classList.remove('active');
}

function showConfirm(title, message, callback) {
    document.getElementById('confirm-title').textContent = title;
    document.getElementById('confirm-message').textContent = message;
    confirmCallback = callback;
    openModal('confirm-modal');
}

function handleConfirm() {
    if (confirmCallback) {
        confirmCallback();
        confirmCallback = null;
    }
    closeModal('confirm-modal');
}

// ===== Toast Functions =====
function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    const icons = {
        success: '✓',
        error: '✕',
        info: 'ℹ'
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

// ===== Tab Navigation =====
function switchTab(tabName) {
    // Update nav buttons
    document.querySelectorAll('.nav__btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.tab === tabName);
    });

    // Update sections
    document.querySelectorAll('.section').forEach(section => {
        section.classList.add('hidden');
    });
    document.getElementById(`${tabName}-section`).classList.remove('hidden');
}

// ===== Utilities =====
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// ===== Event Listeners =====
document.addEventListener('DOMContentLoaded', async () => {
    // Load from server first
    const loaded = await loadDataFromServer();
    if (!loaded) {
        showToast('Не удалось загрузить данные с сервера', 'error');
    }

    renderAll();

    // Admin toggle
    document.getElementById('admin-toggle').addEventListener('click', toggleAdminMode);

    // PIN modal
    document.getElementById('pin-submit').addEventListener('click', checkPin);
    document.getElementById('pin-cancel').addEventListener('click', () => closeModal('pin-modal'));
    document.getElementById('pin-input').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') checkPin();
    });

    // Task modal
    document.getElementById('add-task-btn').addEventListener('click', () => openTaskModal());
    document.getElementById('task-save').addEventListener('click', saveTask);
    document.getElementById('task-cancel').addEventListener('click', () => closeModal('task-modal'));
    document.getElementById('task-delete').addEventListener('click', deleteTask);

    // Shop modal
    document.getElementById('add-shop-btn').addEventListener('click', () => openShopModal());
    document.getElementById('shop-save').addEventListener('click', saveShopItem);
    document.getElementById('shop-cancel').addEventListener('click', () => closeModal('shop-modal'));
    document.getElementById('shop-delete').addEventListener('click', deleteShopItem);

    // Confirm modal
    document.getElementById('confirm-ok').addEventListener('click', handleConfirm);
    document.getElementById('confirm-cancel').addEventListener('click', () => {
        confirmCallback = null;
        closeModal('confirm-modal');
    });

    // Tab navigation
    document.querySelectorAll('.nav__btn').forEach(btn => {
        btn.addEventListener('click', () => switchTab(btn.dataset.tab));
    });

    // Close modals on backdrop click
    document.querySelectorAll('.modal__backdrop').forEach(backdrop => {
        backdrop.addEventListener('click', () => {
            const modal = backdrop.closest('.modal');
            if (modal) {
                modal.classList.remove('active');
                confirmCallback = null;
            }
        });
    });
});
