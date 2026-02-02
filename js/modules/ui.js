import { state } from './state.js';
import { escapeHtml } from './utils.js';

// Helper to access global CONFIG
const CONFIG = window.CONFIG || {
    MONTHLY_LIMIT: 2000,
    RSD_SYMBOL: 'RSD',
    PERIODS: {
        day: { display: 'день' },
        week: { display: 'нед' },
        month: { display: 'мес' }
    },
    SHOP_ITEM_TYPES: {
        small: { label: 'Мелочь' },
        medium: { label: 'Среднее' },
        large: { label: 'Крупное' }
    }
};

export function updateBalanceUI() {
    const balanceEl = document.getElementById('balance');
    if (balanceEl) balanceEl.textContent = state.balance;
    updateBudgetStats();
}

function getMonthlyStats(monthKey) {
    let rsdSpent = 0;
    let largePurchase = null;
    let itemCounts = {};

    state.history.forEach(entry => {
        if (entry.type !== 'spend' || !entry.date.startsWith(monthKey)) return;

        if (entry.rsdAmount) rsdSpent += entry.rsdAmount;

        if (entry.itemId) {
            itemCounts[entry.itemId] = (itemCounts[entry.itemId] || 0) + 1;
            const item = state.shopItems.find(i => i.id === entry.itemId);
            if (item && item.type === 'large') {
                largePurchase = item.name;
            }
        }
    });

    return { rsdSpent, largePurchase, itemCounts };
}

function updateBudgetStats() {
    const currentMonth = new Date().toISOString().slice(0, 7);
    const stats = getMonthlyStats(currentMonth);

    if (document.getElementById('rsd-spent')) {
        document.getElementById('rsd-spent').textContent = stats.rsdSpent.toLocaleString();

        const progress = Math.min((stats.rsdSpent / CONFIG.MONTHLY_LIMIT) * 100, 100);
        const bar = document.getElementById('rsd-progress');
        if (bar) {
            bar.style.width = `${progress}%`;
            bar.className = 'progress-bar';
            if (progress > 90) bar.classList.add('danger');
            else if (progress > 70) bar.classList.add('warning');
        }

        const largeEl = document.getElementById('large-purchase');
        const largeIcon = document.getElementById('large-icon');

        if (largeEl && largeIcon) {
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
}

export function renderTasks() {
    const container = document.getElementById('tasks-list');
    const emptyState = document.getElementById('tasks-empty');
    if (!container) return;

    if (state.tasks.length === 0) {
        container.innerHTML = '';
        if (emptyState) emptyState.classList.remove('hidden');
        return;
    }

    if (emptyState) emptyState.classList.add('hidden');
    container.innerHTML = state.tasks.slice().reverse().map(task => {
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
                ${state.isAdmin ? `
                    <button class="btn btn--success btn--small" onclick="window.app.earnCoins(${task.id})">
                        ✓ Начислить
                    </button>
                    <button class="btn btn--secondary btn--small" onclick="window.app.editTask(${task.id})">
                        ✏️ Изменить
                    </button>
                ` : `
                    <button class="btn btn--primary btn--small" onclick="window.app.requestCoins(${task.id})">
                        ✋ Выполнено
                    </button>
                `}
            </div>
        </div>
        `;
    }).join('');
}

export function renderShop() {
    const container = document.getElementById('shop-list');
    const emptyState = document.getElementById('shop-empty');
    if (!container) return;

    if (state.shopItems.length === 0) {
        container.innerHTML = '';
        if (emptyState) emptyState.classList.remove('hidden');
        return;
    }

    if (emptyState) emptyState.classList.add('hidden');
    container.innerHTML = state.shopItems.map(item => {
        const canAfford = state.balance >= item.price;

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
                            onclick="window.app.buyItem(${item.id})" 
                            ${!canAfford ? 'disabled style="opacity:0.5;cursor:not-allowed;"' : ''}>
                        🛒 ${canAfford ? 'Купить' : 'Не хватает'}
                    </button>
                    ${state.isAdmin ? `
                        <button class="btn btn--secondary btn--small" onclick="window.app.editShopItem(${item.id})">
                            ✏️ Изменить
                        </button>
                    ` : ''}
                </div>
            </div>
        `;
    }).join('');
}

export function renderRequests() {
    const incomingList = document.getElementById('incoming-requests-list');
    const incomingEmpty = document.getElementById('incoming-requests-empty');
    const myList = document.getElementById('my-requests-list');
    const myEmpty = document.getElementById('my-requests-empty');

    if (!incomingList || !myList) return;

    const myPending = state.requests.filter(r => r.status === 'pending');

    if (myPending.length === 0) {
        myList.innerHTML = '';
        if (myEmpty) myEmpty.classList.remove('hidden');
    } else {
        if (myEmpty) myEmpty.classList.add('hidden');
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
                     <button class="btn btn--danger btn--small" onclick="window.app.deleteRequest(${req.id})">🗑️</button>
                </div>
            </div>
        `).join('');
    }

    // Admin View
    const adminSection = document.getElementById('requests-section')?.querySelector('.admin-only');
    if (state.isAdmin) {
        const incoming = state.requests.filter(r => r.status === 'pending');
        if (adminSection) adminSection.classList.remove('hidden');

        if (incoming.length === 0) {
            incomingList.innerHTML = '';
            if (incomingEmpty) incomingEmpty.classList.remove('hidden');
        } else {
            if (incomingEmpty) incomingEmpty.classList.add('hidden');
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
                         <button class="btn btn--success btn--small" onclick="window.app.approveRequest(${req.id})">✅</button>
                         <button class="btn btn--danger btn--small" onclick="window.app.rejectRequest(${req.id})">❌</button>
                    </div>
                </div>
             `).join('');
        }
    } else {
        if (adminSection) adminSection.classList.add('hidden');
    }
}

export function renderHistory() {
    const container = document.getElementById('history-list');
    const emptyState = document.getElementById('history-empty');
    if (!container) return;

    if (state.history.length === 0) {
        container.innerHTML = '';
        if (emptyState) emptyState.classList.remove('hidden');
        return;
    }

    if (emptyState) emptyState.classList.add('hidden');
    container.innerHTML = state.history.slice(0, 50).map(entry => {
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
                     <button class="btn btn--danger btn--small" onclick="window.app.deleteHistoryItem(${entry.id})">🗑️</button>
                </div>
            </div>
        `;
    }).join('');
}

export function renderAll() {
    updateBalanceUI();
    renderTasks();
    renderRequests();
    renderShop();
    renderHistory();
    updateAdminUI();
}

export function updateAdminUI() {
    const adminBtn = document.getElementById('admin-toggle');
    if (adminBtn) {
        adminBtn.classList.toggle('active', state.isAdmin);
        const textSpan = adminBtn.querySelector('.btn__text');
        if (textSpan) textSpan.textContent = state.isAdmin ? 'Выход' : 'Вход админа';
    }

    document.querySelectorAll('.admin-only').forEach(el => {
        el.classList.toggle('hidden', !state.isAdmin);
    });

    const settingsBtn = document.getElementById('settings-btn');
    if (settingsBtn) settingsBtn.classList.toggle('hidden', !state.isAdmin);
}
