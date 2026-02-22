import { escapeHtml } from './utils.js';

function renderHistoryItem(entry) {
    const isEarn = entry.type === 'earn';
    const formattedDate = new Date(entry.date).toLocaleDateString('ru-RU', {
        day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit'
    });
    const moneyVal = entry.moneyAmount || entry.rsdAmount;
    const moneyTag = moneyVal ? `<span class="tag tag--money" style="font-size:0.75em;margin-left:0.5em;">${moneyVal}</span>` : '';

    return `
        <div class="history-item history-item--${entry.type}">
            <div class="history-item__icon">${isEarn ? '💰' : '🛍️'}</div>
            <div class="history-item__content">
                <div class="history-item__desc">${escapeHtml(entry.description)}${moneyTag}</div>
                <div class="history-item__date">${formattedDate}</div>
            </div>
            <div class="history-item__amount">${isEarn ? '+' : '-'}${entry.amount} 🪙</div>
            <div class="card__actions" style="margin-left: 10px;">
                 <button class="btn btn--danger btn--small" onclick="window.app.deleteHistoryItem(${entry.id})">🗑️</button>
            </div>
        </div>
    `;
}

function renderMonthHeader(monthName, stats) {
    const moneySpent = stats.moneySpent > 0 ? ` | <span class="money">-${stats.moneySpent.toLocaleString()} 💸</span>` : '';
    return `
        <div class="history-month-header">
            <div class="month-title">${monthName}</div>
            <div class="month-stats">
                <span class="earn">+${stats.earned} 🪙</span> | <span class="spend">-${stats.spent} 🪙</span>${moneySpent}
            </div>
        </div>
    `;
}

export function renderHistoryUI(state) {
    const container = document.getElementById('history-list');
    const emptyState = document.getElementById('history-empty');
    if (!container) return;

    let history = state.history;
    if (state.isAdmin && state.currentChildId) {
        history = state.history.filter(h => h.childId == state.currentChildId);
    }
    if (history.length === 0) {
        container.innerHTML = '';
        if (emptyState) emptyState.classList.remove('hidden');
        return;
    }
    if (emptyState) emptyState.classList.add('hidden');

    const grouped = history.reduce((acc, entry) => {
        if (!entry.date) return acc;
        const dateStr = typeof entry.date === 'string' ? entry.date : new Date(entry.date).toISOString();
        const monthKey = dateStr.slice(0, 7);
        if (!acc[monthKey]) acc[monthKey] = { items: [], earned: 0, spent: 0, moneySpent: 0 };
        acc[monthKey].items.push(entry);
        if (entry.type === 'earn') acc[monthKey].earned += entry.amount;
        else {
            acc[monthKey].spent += entry.amount;
            acc[monthKey].moneySpent += (entry.moneyAmount || entry.rsdAmount || 0);
        }
        return acc;
    }, {});

    container.innerHTML = Object.keys(grouped).sort().reverse().map(monthKey => {
        const [year, month] = monthKey.split('-');
        const name = new Date(year, month - 1).toLocaleString('ru-RU', { month: 'long', year: 'numeric' });
        const items = grouped[monthKey].items.sort((a, b) => new Date(b.date) - new Date(a.date)).map(renderHistoryItem).join('');
        return renderMonthHeader(name, grouped[monthKey]) + items;
    }).join('');
}
