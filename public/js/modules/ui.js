import { state } from './state.js';
import { escapeHtml } from './utils.js';
import { updateBudgetStatsUI } from './budget-ui.js';
import { renderChildSwitcherUI } from './child-switcher-ui.js';

// Helper to access global CONFIG
const CONFIG = window.CONFIG || {
    MONTHLY_LIMIT: 10000,
    PERIODS: {
        day: { display: 'день' },
        week: { display: 'нед' },
        month: { display: 'мес' },
        year: { display: 'год' }
    },
    SHOP_ITEM_TYPES: {
        small: { label: 'Мелочь' },
        medium: { label: 'Среднее' },
        large: { label: 'Крупное' }
    }
};

export function updateBalanceUI() {
    let displayBalance = state.balance;
    if (state.isAdmin && state.currentChildId) {
        const child = state.children.find(c => c.id == state.currentChildId);
        if (child) displayBalance = child.balance;
    }
    const balanceEl = document.getElementById('balance');
    if (balanceEl) balanceEl.textContent = displayBalance;
    updateBudgetStatsUI(state, CONFIG, getActiveChildId());
}

function getActiveChildId() {
    if (state.isAdmin) return state.currentChildId || null;
    if (state.children && state.children.length > 0) return state.children[0].id;
    return null;
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

    // Filter tasks based on currentChildId if admin
    let tasksToRender = state.tasks;
    if (state.isAdmin && state.currentChildId) {
        tasksToRender = state.tasks.filter(t => t.childId == state.currentChildId);
    }

    // Grouping logic
    const grouped = tasksToRender.reduce((acc, t) => {
        const g = t.group || 'Без категории';
        if (!acc[g]) acc[g] = [];
        acc[g].push(t);
        return acc;
    }, {});

    let html = '';
    const sortedGroups = Object.keys(grouped).sort((a, b) => {
        if (a === 'Без категории') return 1;
        if (b === 'Без категории') return -1;
        return a.localeCompare(b);
    });

    sortedGroups.forEach(groupName => {
        html += `<div class="group-header">${escapeHtml(groupName)}</div>`;
        html += grouped[groupName].sort((a, b) => a.coins - b.coins).map(task => {
            let tags = [];
            if (task.frequency && task.frequency.period) {
                const periodInfo = CONFIG.PERIODS[task.frequency.period];
                const display = periodInfo ? periodInfo.display : task.frequency.period;
                tags.push(`<span class="tag">${task.frequency.limit}/${display}</span>`);
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
    });
    container.innerHTML = html;
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

    // Filter shop items
    let shopToRender = state.shopItems;
    if (state.isAdmin && state.currentChildId) {
        shopToRender = state.shopItems.filter(i => i.childId == state.currentChildId);
    }

    // Grouping logic
    const grouped = shopToRender.reduce((acc, item) => {
        const g = item.group || 'Без категории';
        if (!acc[g]) acc[g] = [];
        acc[g].push(item);
        return acc;
    }, {});

    let html = '';
    const sortedGroups = Object.keys(grouped).sort((a, b) => {
        if (a === 'Без категории') return 1;
        if (b === 'Без категории') return -1;
        return a.localeCompare(b);
    });

    sortedGroups.forEach(groupName => {
        html += `<div class="group-header">${escapeHtml(groupName)}</div>`;
        html += grouped[groupName].sort((a, b) => a.price - b.price).map(item => {
            const canAfford = state.balance >= item.price;

            let tags = [];
            if (item.type) {
                const label = CONFIG.SHOP_ITEM_TYPES[item.type] ? CONFIG.SHOP_ITEM_TYPES[item.type].label : item.type;
                tags.push(`<span class="tag tag--${item.type}">${label}</span>`);
            }
            const mLimit = item.moneyLimit || item.money_limit;
            if (mLimit) {
                tags.push(`<span class="tag tag--money">Lim: ${mLimit} 🪙</span>`);
            }
            if (item.frequency && item.frequency.period) {
                const periodInfo = CONFIG.PERIODS[item.frequency.period];
                const display = periodInfo ? periodInfo.display : item.frequency.period;
                tags.push(`<span class="tag">${item.frequency.limit}/${display}</span>`);
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
    });
    container.innerHTML = html;
}

export function renderRequests() {
    const incomingList = document.getElementById('incoming-requests-list');
    const incomingEmpty = document.getElementById('incoming-requests-empty');
    const myList = document.getElementById('my-requests-list');
    const myEmpty = document.getElementById('my-requests-empty');

    // Parent should see requests for all children.
    let relevantRequests = state.requests;

    // Update Counter
    const pendingCount = relevantRequests.filter(r => r.status === 'pending').length;
    const navBadge = document.getElementById('requests-counter');
    if (navBadge) {
        navBadge.textContent = pendingCount;
        navBadge.classList.toggle('hidden', pendingCount === 0);
    }

    if (!incomingList || !myList) return;

    if (state.isAdmin) {
        myList.innerHTML = '';
        if (myEmpty) myEmpty.classList.add('hidden');
    } else {
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
                        <div class="history-item__desc">${escapeHtml(req.taskName)}</div>
                        <div class="history-item__date">Ожидает подтверждения</div>
                    </div>
                    <div class="history-item__amount">
                        ${req.requestType === 'shop_purchase' ? '-' : '+'}${req.coins} 🪙
                        ${(req.moneyAmount || 0) > 0 ? `<span class="tag tag--money" style="margin-left: 5px;">${req.moneyAmount}</span>` : ''}
                    </div>
                    <div class="card__actions" style="margin-left: 10px;">
                         <button class="btn btn--danger btn--small" onclick="window.app.deleteRequest(${req.id})">🗑️</button>
                    </div>
                </div>
            `).join('');
        }
    }

    // Admin View
    const adminSection = document.getElementById('requests-section')?.querySelector('.admin-only');
    if (state.isAdmin) {
        const incoming = relevantRequests.filter(r => r.status === 'pending');
        if (adminSection) adminSection.classList.remove('hidden');

        if (incoming.length === 0) {
            incomingList.innerHTML = '';
            if (incomingEmpty) incomingEmpty.classList.remove('hidden');
        } else {
            if (incomingEmpty) incomingEmpty.classList.add('hidden');
            incomingList.innerHTML = incoming.map(req => {
                // Find child name
                const child = state.children.find(c => c.id == req.childId);
                const childName = child ? child.name : 'Unknown';

                return `
                <div class="history-item">
                    <div class="history-item__icon">📩</div>
                    <div class="history-item__content">
                        <div class="history-item__desc">
                            <span class="tag" style="margin-right: 5px;">${escapeHtml(childName)}</span> 
                            ${req.requestType === 'shop_purchase' ? 'Покупка: ' : 'Задание: '}
                            ${escapeHtml(req.taskName)}
                        </div>
                        <div class="history-item__date">${new Date(req.date).toLocaleString()}</div>
                    </div>
                    <div class="history-item__amount">
                        ${req.requestType === 'shop_purchase' ? '-' : '+'}${req.coins} 🪙
                        ${(req.moneyAmount || 0) > 0 ? `<span class="tag tag--money" style="margin-left: 5px;">${req.moneyAmount}</span>` : ''}
                    </div>
                    <div class="card__actions" style="margin-left: 10px;">
                         <button class="btn btn--success btn--small" onclick="window.app.approveRequest(${req.id})">✅</button>
                         <button class="btn btn--danger btn--small" onclick="window.app.rejectRequest(${req.id})">❌</button>
                    </div>
                </div>
             `}).join('');
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

    // Filter history
    let historyToRender = state.history;
    if (state.isAdmin && state.currentChildId) {
        historyToRender = state.history.filter(h => h.childId == state.currentChildId);
    }

    // Grouping history by month
    const grouped = historyToRender.reduce((acc, entry) => {
        const date = new Date(entry.date);
        const monthKey = entry.date.slice(0, 7); // YYYY-MM
        if (!acc[monthKey]) {
            acc[monthKey] = {
                items: [],
                earned: 0,
                spent: 0,
                moneySpent: 0
            };
        }
        acc[monthKey].items.push(entry);
        if (entry.type === 'earn') {
            acc[monthKey].earned += entry.amount;
        } else {
            acc[monthKey].spent += entry.amount;
            acc[monthKey].moneySpent += (entry.moneyAmount || entry.rsdAmount || 0);
        }
        return acc;
    }, {});

    let html = '';
    const sortedMonths = Object.keys(grouped).sort().reverse();

    sortedMonths.forEach(monthKey => {
        const [year, month] = monthKey.split('-');
        const monthName = new Date(year, month - 1).toLocaleString('ru-RU', { month: 'long', year: 'numeric' });
        const stats = grouped[monthKey];

        html += `
            <div class="history-month-header">
                <div class="month-title">${monthName}</div>
                <div class="month-stats">
                    <span class="earn">+${stats.earned} 🪙</span> | 
                    <span class="spend">-${stats.spent} 🪙</span>
                    ${stats.moneySpent > 0 ? ` | <span class="money">-${stats.moneySpent.toLocaleString()} 💸</span>` : ''}
                </div>
            </div>
        `;

        html += stats.items.sort((a, b) => new Date(b.date) - new Date(a.date)).map(entry => {
            const isEarn = entry.type === 'earn';
            const itemDate = new Date(entry.date);
            const formattedDate = itemDate.toLocaleDateString('ru-RU', {
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
                            ${(entry.moneyAmount || entry.rsdAmount) ? `<span class="tag tag--money" style="font-size:0.75em;margin-left:0.5em;">${entry.moneyAmount || entry.rsdAmount}</span>` : ''}
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
    });

    container.innerHTML = html;
}

export function renderFriends() {
    const container = document.getElementById('friends-list');
    const emptyState = document.getElementById('friends-empty');
    if (!container) return;

    if (!state.friends || state.friends.length === 0) {
        container.innerHTML = '';
        if (emptyState) emptyState.classList.remove('hidden');
        return;
    }

    let friendsToRender = state.friends;
    if (state.isAdmin && state.currentChildId) {
        // If we have ownerChildId info (from repository update)
        friendsToRender = state.friends.filter(f => !f.ownerChildId || f.ownerChildId == state.currentChildId);
    }

    if (friendsToRender.length === 0) {
        container.innerHTML = '';
        if (emptyState) emptyState.classList.remove('hidden');
        return;
    }

    if (emptyState) emptyState.classList.add('hidden');

    container.innerHTML = friendsToRender.map(friend => `
        <div class="friend-item">
            <div class="friend-info">
                <span class="friend-nickname">${escapeHtml(friend.nickname)}</span>
                <span class="friend-balance">💰 ${friend.balance} 🪙</span>
            </div>
            <div class="friend-actions">
                <!-- Можно добавить удаление или другие действия -->
            </div>
        </div>
    `).join('');
}

export function renderAll() {
    updateBalanceUI();
    renderTasks();
    renderRequests();
    renderShop();
    renderHistory();
    renderFriends();
    updateAdminUI();
    updateShopNameUI();
    renderChildSwitcher(); // Add this
}

export function renderChildSwitcher() {
    renderChildSwitcherUI(state, escapeHtml);
}

export function updateAdminUI() {
    document.querySelectorAll('.admin-only').forEach(el => {
        el.classList.toggle('hidden', !state.isAdmin);
    });

    document.querySelectorAll('.parent-only').forEach(el => {
        el.classList.toggle('hidden', !state.isAdmin);
    });

    document.querySelectorAll('.child-only').forEach(el => {
        el.classList.toggle('hidden', state.isAdmin);
    });

    // Keep settings button visible for everyone to access profiles/nicknames
    const settingsBtn = document.getElementById('settings-btn') || document.getElementById('nav-settings');
    if (settingsBtn) settingsBtn.classList.remove('hidden');
}

export function updateShopNameUI() {
    const shopNameEl = document.getElementById('shop-name-display');
    if (shopNameEl) {
        shopNameEl.textContent = state.familyName || '';
    }

    const nicknameEl = document.getElementById('child-nickname-display');
    if (nicknameEl) {
        nicknameEl.textContent = state.childNickname ? `(${state.childNickname})` : '';
    }

    const nameInp = document.getElementById('settings-family-name-inline');
    if (nameInp) {
        nameInp.value = state.familyName || '';
    }

    const nicknameInp = document.getElementById('settings-nickname');
    if (nicknameInp) {
        nicknameInp.value = state.childNickname || '';
    }
}
