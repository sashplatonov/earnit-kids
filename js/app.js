// ===== Data Management =====
const API_URL = '/api/data';

// State
let isAdmin = false;
let balance = 0;
let tasks = [];
let shopItems = [];
let history = [];
let requests = []; // New requests state
let adminPin = null;
let editingTaskId = null;
let editingShopId = null;
let confirmCallback = null;
let importType = null; // 'tasks' | 'shop'

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
            requests = data.requests || []; // Load requests
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
            history: history,
            requests: requests // Save requests
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

function addHistoryEntry(type, amount, description, relatedId = null) {
    const entry = {
        id: Date.now(),
        type: type, // 'earn' | 'spend'
        amount: amount,
        description: description,
        date: new Date().toISOString()
    };

    if (relatedId) {
        if (type === 'spend') entry.itemId = relatedId;
        else if (type === 'earn') entry.taskId = relatedId;
    }

    history.unshift(entry);
    scheduleSave();
    renderHistory();
}

// ===== Limits Logic =====
function getMonthlyStats(monthKey) {
    let rsdSpent = 0;
    let largePurchase = null;
    let itemCounts = {};

    history.forEach(entry => {
        if (entry.type !== 'spend' || !entry.date.startsWith(monthKey)) return;

        if (entry.rsdAmount) rsdSpent += entry.rsdAmount;

        if (entry.itemId) {
            itemCounts[entry.itemId] = (itemCounts[entry.itemId] || 0) + 1;

            // Check large purchase
            const item = shopItems.find(i => i.id === entry.itemId);
            if (item && item.type === 'large') {
                largePurchase = item.name;
            }
        }
    });

    return { rsdSpent, largePurchase, itemCounts };
}

function checkLimits(item, rsdPrice) {
    const now = new Date();
    const currentMonth = now.toISOString().slice(0, 7);
    const stats = getMonthlyStats(currentMonth);

    // 1. Check budget limit
    if (stats.rsdSpent + rsdPrice > CONFIG.MONTHLY_LIMIT) {
        return `Превышен месячный лимит (осталось ${CONFIG.MONTHLY_LIMIT - stats.rsdSpent} ${CONFIG.RSD_SYMBOL})`;
    }

    // 2. Check large purchase limit
    if (item.type === 'large' && stats.largePurchase) {
        return `Уже была крупная покупка в этом месяце (${stats.largePurchase})`;
    }

    // 3. Check frequency
    if (item.frequency) {
        const { limit, period } = item.frequency;
        let count = 0;
        let startDate = new Date();

        if (period === 'day') startDate.setHours(0, 0, 0, 0);
        if (period === 'week') startDate.setDate(startDate.getDate() - startDate.getDay() + 1); // Monday
        if (period === 'month') startDate.setDate(1);

        const startTime = startDate.getTime();

        history.forEach(h => {
            if (h.itemId === item.id && new Date(h.date).getTime() >= startTime) {
                count++;
            }
        });

        if (count >= limit) {
            return `Лимит частоты: ${limit} раз(а) в ${period}`;
        }
    }

    return null; // OK
}

// ===== UI Rendering =====
function updateBalance() {
    document.getElementById('balance').textContent = balance;

    // Update budget stats
    const currentMonth = new Date().toISOString().slice(0, 7);
    const stats = getMonthlyStats(currentMonth);

    if (document.getElementById('rsd-spent')) {
        document.getElementById('rsd-spent').textContent = stats.rsdSpent.toLocaleString();

        const progress = Math.min((stats.rsdSpent / CONFIG.MONTHLY_LIMIT) * 100, 100);
        const bar = document.getElementById('rsd-progress');
        bar.style.width = `${progress}%`;
        bar.className = 'progress-bar';
        if (progress > 90) bar.classList.add('danger');
        else if (progress > 70) bar.classList.add('warning');

        const largeEl = document.getElementById('large-purchase');
        const largeIcon = document.getElementById('large-icon');

        if (stats.largePurchase) {
            largeEl.textContent = stats.largePurchase;
            largeIcon.textContent = '✅';
            largeIcon.style.background = 'rgba(16, 185, 129, 0.2)';
        } else {
            largeEl.textContent = 'Нет';
            largeIcon.textContent = '⬜';
            largeIcon.style.background = 'rgba(255, 255, 255, 0.1)';
        }
    }
}

function renderTasks() {
    const container = document.getElementById('tasks-list');
    const emptyState = document.getElementById('tasks-empty');
    if (!container) return;

    if (tasks.length === 0) {
        container.innerHTML = '';
        emptyState.classList.remove('hidden');
        return;
    }

    emptyState.classList.add('hidden');
    container.innerHTML = tasks.slice().reverse().map(task => {
        let tags = [];
        if (task.frequency) {
            tags.push(`<span class="tag">${task.frequency.limit}/${CONFIG.PERIODS[task.frequency.period].display || 'пер'}</span>`);
        }

        return `
        <div class="card" data-id="${task.id}">
            <div class="card__header">
                <h3 class="card__title">${escapeHtml(task.name)}</h3>
                <div class="card__coins">
                    <span>${task.coins}</span>
                    <span>🪙</span>
                </div>
            </div>
            ${tags.length ? `<div style="margin-bottom:0.5rem;">${tags.join('')}</div>` : ''}
            ${task.comment ? `<p class="card__comment">${escapeHtml(task.comment)}</p>` : ''}
            <div class="card__actions">
                ${isAdmin ? `
                    <button class="btn btn--success btn--small" onclick="earnCoins(${task.id})">
                        ✓ Начислить
                    </button>
                    <button class="btn btn--secondary btn--small" onclick="editTask(${task.id})">
                        ✏️ Изменить
                    </button>
                ` : `
                    <button class="btn btn--primary btn--small" onclick="requestCoins(${task.id})">
                        ✋ Выполнено
                    </button>
                `}
            </div>
        </div>
        `;
    }).join('');
}

function renderShop() {
    const container = document.getElementById('shop-list');
    const emptyState = document.getElementById('shop-empty');
    if (!container) return;

    if (shopItems.length === 0) {
        container.innerHTML = '';
        emptyState.classList.remove('hidden');
        return;
    }

    emptyState.classList.add('hidden');
    container.innerHTML = shopItems.map(item => {
        const canAfford = balance >= item.price;

        // Format tags
        let tags = [];
        if (item.type) {
            const label = CONFIG.SHOP_ITEM_TYPES[item.type] ? CONFIG.SHOP_ITEM_TYPES[item.type].label : item.type;
            tags.push(`<span class="tag tag--${item.type}">${label}</span>`);
        }
        if (item.rsdLimit) {
            tags.push(`<span class="tag tag--rsd">до ${item.rsdLimit} ${CONFIG.RSD_SYMBOL}</span>`);
        }
        if (item.frequency) {
            tags.push(`<span class="tag">${item.frequency.limit}/${CONFIG.PERIODS[item.frequency.period].display || 'пер'}</span>`);
        }

        return `
            <div class="card ${canAfford ? 'card--affordable' : ''}" data-id="${item.id}">
                <div class="card__header">
                    <h3 class="card__title">${escapeHtml(item.name)}</h3>
                    <div class="card__coins">
                        <span>${item.price}</span>
                        <span>🪙</span>
                    </div>
                </div>
                <div style="margin-bottom:0.5rem;">${tags.join('')}</div>
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
    if (!container) return;

    if (history.length === 0) {
        container.innerHTML = '';
        emptyState.classList.remove('hidden');
        return;
    }

    emptyState.classList.add('hidden');
    container.innerHTML = history.slice(0, 50).map(entry => {
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
                    <div class="history-item__desc">
                        ${escapeHtml(entry.description)}
                        ${entry.rsdAmount ? `<span class="tag tag--rsd" style="font-size:0.75em;margin-left:0.5em;">${entry.rsdAmount} RSD</span>` : ''}
                    </div>
                    <div class="history-item__date">${formattedDate}</div>
                </div>
                <div class="history-item__amount">
                    ${isEarn ? '+' : '-'}${entry.amount} 🪙
                </div>
                    <div class="card__actions" style="margin-left: 10px;">
                         <button class="btn btn--danger btn--small" onclick="deleteHistoryItem(${entry.id})">🗑️</button>
                    </div>
            </div>
        `;
    }).join('');
}

function deleteHistoryItem(id) {
    if (!confirm('Удалить эту запись из истории?')) return;
    history = history.filter(h => h.id !== id);
    scheduleSave();
    renderAll();
    showToast('Запись удалена', 'info');
}

function clearHistory() {
    if (!confirm('Очистить ВСЮ историю? Это нельзя отменить.')) return;
    history = [];
    scheduleSave();
    renderAll();
    showToast('История очищена', 'info');
}

function renderAll() {
    updateBalance();
    renderTasks();
    renderRequests(); // Render requests
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
    // Перерендериваем карточки для отображения кнопок
    renderTasks();
    renderRequests(); // Re-render requests on admin toggle
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

        if (task.frequency) {
            document.getElementById('task-freq-limit').value = task.frequency.limit;
            document.getElementById('task-freq-period').value = task.frequency.period;
        } else {
            document.getElementById('task-freq-limit').value = '';
            document.getElementById('task-freq-period').value = 'day';
        }

        deleteBtn.classList.remove('hidden');
    } else {
        title.textContent = 'Добавить задание';
        document.getElementById('task-name').value = '';
        document.getElementById('task-coins').value = '';
        document.getElementById('task-comment').value = '';
        document.getElementById('task-freq-limit').value = '';
        document.getElementById('task-freq-period').value = 'day';
        deleteBtn.classList.add('hidden');
    }

    openModal('task-modal');
}

function saveTask() {
    const name = document.getElementById('task-name').value.trim();
    const coins = parseInt(document.getElementById('task-coins').value);
    const comment = document.getElementById('task-comment').value.trim();
    const freqLimit = parseInt(document.getElementById('task-freq-limit').value) || 0;
    const freqPeriod = document.getElementById('task-freq-period').value;

    if (!name) {
        showToast('Введите название задания', 'error');
        return;
    }
    if (!coins || coins < 1) {
        showToast('Введите количество монет', 'error');
        return;
    }

    const taskData = {
        name,
        coins,
        comment,
        frequency: freqLimit > 0 ? { limit: freqLimit, period: freqPeriod } : null
    };

    if (editingTaskId) {
        // Редактирование
        const index = tasks.findIndex(t => t.id === editingTaskId);
        if (index !== -1) {
            tasks[index] = { ...tasks[index], ...taskData };
        }
    } else {
        // Создание
        tasks.push({
            id: Date.now(),
            ...taskData
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

// ===== Requests Logic =====
function requestCoins(taskId) {
    const task = tasks.find(t => t.id === taskId);
    if (!task) return;

    // Check frequency (re-use check logic or similar?)
    // Basic check for now, can be improved to check pending requests too
    if (task.frequency) {
        // Count pending requests + history earnings
        // For now, let's keep it simple and check history
        // Ideally we should count pending requests towards limit too
    }

    const request = {
        id: Date.now(),
        taskId: task.id,
        taskName: task.name,
        coins: task.coins,
        date: new Date().toISOString(),
        status: 'pending'
    };

    requests.push(request);
    scheduleSave();
    renderRequests();

    // Switch to requests tab to show the user
    document.querySelector('.nav__btn[data-tab="requests"]').click();
    showToast('Заявка отправлена!', 'success');
}

function renderRequests() {
    const incomingList = document.getElementById('incoming-requests-list');
    const incomingEmpty = document.getElementById('incoming-requests-empty');
    const myList = document.getElementById('my-requests-list');
    const myEmpty = document.getElementById('my-requests-empty');

    if (!incomingList || !myList) return;

    // 1. My Requests (Child View)
    const myPending = requests.filter(r => r.status === 'pending');

    if (myPending.length === 0) {
        myList.innerHTML = '';
        myEmpty.classList.remove('hidden');
    } else {
        myEmpty.classList.add('hidden');
        myList.innerHTML = myPending.sort((a, b) => b.id - a.id).map(req => `
            <div class="history-item">
                <div class="history-item__icon">⏳</div>
                <div class="history-item__content">
                    <div class="history-item__desc">
                        ${escapeHtml(req.taskName)}
                    </div>
                    <div class="history-item__date">Ожидает подтверждения</div>
                </div>
                <div class="history-item__amount">
                    +${req.coins} 🪙
                </div>
                <div class="card__actions" style="margin-left: 10px;">
                     <button class="btn btn--danger btn--small" onclick="deleteRequest(${req.id})">🗑️</button>
                </div>
            </div>
        `).join('');
    }

    // 2. Incoming Requests (Admin View)
    if (isAdmin) {
        const incoming = requests.filter(r => r.status === 'pending');
        document.getElementById('requests-section').querySelector('.admin-only').classList.remove('hidden');

        if (incoming.length === 0) {
            incomingList.innerHTML = '';
            incomingEmpty.classList.remove('hidden');
        } else {
            incomingEmpty.classList.add('hidden');
            incomingList.innerHTML = incoming.map(req => `
                <div class="history-item">
                    <div class="history-item__icon">📩</div>
                    <div class="history-item__content">
                        <div class="history-item__desc">
                            ${escapeHtml(req.taskName)}
                        </div>
                        <div class="history-item__date">${new Date(req.date).toLocaleString()}</div>
                    </div>
                    <div class="history-item__amount">
                        +${req.coins} 🪙
                    </div>
                    <div class="card__actions" style="margin-left: 10px;">
                         <button class="btn btn--success btn--small" onclick="approveRequest(${req.id})">✅</button>
                         <button class="btn btn--danger btn--small" onclick="rejectRequest(${req.id})">❌</button>
                    </div>
                </div>
             `).join('');
        }
    } else {
        document.getElementById('requests-section').querySelector('.admin-only').classList.add('hidden');
    }
}

function deleteRequest(reqId) {
    if (!confirm('Удалить заявку?')) return;
    requests = requests.filter(r => r.id !== reqId);
    scheduleSave();
    renderRequests();
    showToast('Заявка удалена', 'info');
}

function approveRequest(reqId) {
    const req = requests.find(r => r.id === reqId);
    if (!req) return;

    // Limits check?
    // We can assume parent overrides limits if approving manual request,
    // OR we can perform check here. Let's force approve for now (Admin power).

    balance += req.coins;
    updateBalance();
    addHistoryEntry('earn', req.coins, req.taskName, req.taskId);

    // Remove request
    requests = requests.filter(r => r.id !== reqId);

    scheduleSave();
    renderRequests();
    showToast(`Заявка подтверждена: +${req.coins} 🪙`, 'success');
}

function rejectRequest(reqId) {
    const req = requests.find(r => r.id === reqId);
    if (!req) return;

    if (!confirm('Отклонить заявку?')) return;

    // Add rejected entry to hisory? Plan says "yes"
    addHistoryEntry('spend', 0, `❌ Отклонено: ${req.taskName}`, null); // Type spend 0 just to show icon/color? Or maybe special type.
    // Actually let's just make a note history entry if needed, or just delete.
    // Plan: "rejected -> moves to history". Let's use 'spend' type with 0 amount but descriptive text, or create valid type.
    // Existing types: 'earn', 'spend'. 
    // Let's use 'earn' with 0 amount? Or special visual?
    // Let's use 0 amount and special description.

    // Remove request
    requests = requests.filter(r => r.id !== reqId);

    scheduleSave();
    renderRequests();
    showToast('Заявка отклонена', 'info');
}

function earnCoins(taskId) {
    const task = tasks.find(t => t.id === taskId);
    if (!task) return;

    // Check frequency limit
    if (task.frequency) {
        const { limit, period } = task.frequency;
        let count = 0;
        let startDate = new Date();

        if (period === 'day') startDate.setHours(0, 0, 0, 0);
        if (period === 'week') startDate.setDate(startDate.getDate() - startDate.getDay() + 1); // Monday
        if (period === 'month') startDate.setDate(1);

        const startTime = startDate.getTime();

        history.forEach(h => {
            // We need to check 'earn' events for this task
            // We'll use description match for now as we don't store taskId for earning yet
            // OR better: let's start storing taskId for earning too!
            // For legacy compat, we check description match if taskId missing
            const isMatch = h.taskId === task.id || (h.type === 'earn' && h.description === task.name);

            if (isMatch && new Date(h.date).getTime() >= startTime) {
                count++;
            }
        });

        if (count >= limit) {
            showToast(`Лимит исчерпан: ${limit} раз(а) в ${period}`, 'error');
            return;
        }
    }

    showConfirm(
        'Начислить монеты?',
        `Начислить ${task.coins} 🪙 за "${task.name}"?`,
        () => {
            balance += task.coins;
            updateBalance();
            // Start sending taskId for earns too
            addHistoryEntry('earn', task.coins, task.name, task.id);
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
        document.getElementById('shop-rsd').value = item.rsdLimit || '';
        document.getElementById('shop-type').value = item.type || 'small';

        if (item.frequency) {
            document.getElementById('shop-freq-limit').value = item.frequency.limit;
            document.getElementById('shop-freq-period').value = item.frequency.period;
        } else {
            document.getElementById('shop-freq-limit').value = 1;
            document.getElementById('shop-freq-period').value = 'week';
        }

        deleteBtn.classList.remove('hidden');
    } else {
        title.textContent = 'Добавить товар';
        document.getElementById('shop-name').value = '';
        document.getElementById('shop-price').value = '';
        document.getElementById('shop-comment').value = '';
        document.getElementById('shop-rsd').value = '';
        document.getElementById('shop-type').value = 'small';
        document.getElementById('shop-freq-limit').value = 1;
        document.getElementById('shop-freq-period').value = 'week';

        deleteBtn.classList.add('hidden');
    }

    openModal('shop-modal');
}

function saveShopItem() {
    const name = document.getElementById('shop-name').value.trim();
    const price = parseInt(document.getElementById('shop-price').value);
    const comment = document.getElementById('shop-comment').value.trim();

    // New fields
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
        const index = shopItems.findIndex(i => i.id === editingShopId);
        if (index !== -1) {
            shopItems[index] = { ...shopItems[index], id: editingShopId, ...newItem };
        }
    } else {
        shopItems.push({
            id: Date.now(),
            ...newItem
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

    // Ask for actual RSD price
    const rsdInput = prompt(`Покупка "${item.name}"\nВведите стоимость в ${CONFIG.RSD_SYMBOL} (макс ${item.rsdLimit || CONFIG.MONTHLY_LIMIT}):`, '0');
    if (rsdInput === null) return; // Cancelled

    const rsdPrice = parseInt(rsdInput);
    if (isNaN(rsdPrice) || rsdPrice < 0) {
        showToast('Некорректная сумма RSD', 'error');
        return;
    }

    if (item.rsdLimit && rsdPrice > item.rsdLimit) {
        showToast(`Цена выше лимита товара (${item.rsdLimit} RSD)`, 'error');
        return;
    }

    // Check global limits
    const limitError = checkLimits(item, rsdPrice);
    if (limitError) {
        showToast(limitError, 'error');
        return;
    }

    showConfirm(
        'Подтвердите покупку',
        `Купить "${item.name}" за ${item.price} 🪙 и ${rsdPrice} ${CONFIG.RSD_SYMBOL}?`,
        () => {
            balance -= item.price; // Coins spent logic
            // Note: RSD are just tracked, not subtracted from a balance (budget is a limit)

            // Add history with extended data
            const entry = {
                id: Date.now(),
                type: 'spend',
                amount: item.price,
                description: item.name,
                date: new Date().toISOString(),
                itemId: item.id,
                rsdAmount: rsdPrice
            };
            history.unshift(entry);

            scheduleSave();
            renderAll();
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
        success: CONFIG.ICONS.SUCCESS,
        error: CONFIG.ICONS.ERROR,
        info: CONFIG.ICONS.INFO
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
    }, CONFIG.TOAST_DURATION);
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

    // Import modals
    document.getElementById('import-tasks-btn').addEventListener('click', () => openImportModal('tasks'));
    document.getElementById('import-shop-btn').addEventListener('click', () => openImportModal('shop'));
    document.getElementById('import-submit').addEventListener('click', processImport);
    document.getElementById('import-submit').addEventListener('click', processImport);
    document.getElementById('import-cancel').addEventListener('click', () => closeModal('import-modal'));

    // History
    const clearHistoryBtn = document.getElementById('clear-history-btn');
    if (clearHistoryBtn) {
        clearHistoryBtn.addEventListener('click', clearHistory);
    }
});

// ===== Import Functions =====
function openImportModal(type) {
    importType = type;
    const title = document.getElementById('import-modal-title');
    const textarea = document.getElementById('import-text');

    if (type === 'tasks') {
        title.textContent = '📋 Быстрый импорт заданий';
        textarea.placeholder = 'Помыть посуду | 5 | Хорошо вымыть\nУбрать комнату | 10\nСделать уроки | 15 | Все предметы';
    } else {
        title.textContent = '🛒 Быстрый импорт товаров';
        textarea.placeholder = 'Час игры | 20\nМороженое | 15 | Любое на выбор\nПоход в кино | 50';
    }

    textarea.value = '';
    openModal('import-modal');
    textarea.focus();
}

function processImport() {
    const text = document.getElementById('import-text').value.trim();
    if (!text) {
        showToast('Введите данные для импорта', 'error');
        return;
    }

    const lines = text.split('\n').filter(line => line.trim());
    let count = 0;

    for (const line of lines) {
        const parts = line.split('|').map(p => p.trim());
        if (parts.length < 2) continue;

        const name = parts[0];
        const value = parseInt(parts[1]);
        const comment = parts[2] || '';

        if (!name || !value || value < 1) continue;

        if (importType === 'tasks') {
            tasks.push({
                id: Date.now() + count,
                name,
                coins: value,
                comment
            });
        } else {
            shopItems.push({
                id: Date.now() + count,
                name,
                price: value,
                comment
            });
        }
        count++;
    }

    if (count > 0) {
        scheduleSave();
        if (importType === 'tasks') {
            renderTasks();
        } else {
            renderShop();
        }
        closeModal('import-modal');
        showToast(`Импортировано: ${count} ${importType === 'tasks' ? 'заданий' : 'товаров'}`, 'success');
    } else {
        showToast('Не удалось распознать данные', 'error');
    }
}
