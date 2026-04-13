/** @file Ui History frontend UI module */
import { escapeHtml } from './utils.js';
import { getCreatedAt, getGroupName } from './server-contract.js';

function getEntryDetails(entry, state) {
    const details = {
        name: entry.name || entry.description || 'Действие',
        group: entry.groupName,
        comment: entry.comment
    };

    if (entry.type === 'earn' && entry.taskId) {
        const t = state.tasks.find(t => String(t.id) === String(entry.taskId));
        if (t) {
            details.name = t.name;
            details.group = getGroupName(t);
            details.comment = t.comment;
        }
    } else if (entry.type === 'spend' && entry.itemId) {
        const i = state.shopItems.find(i => String(i.id) === String(entry.itemId));
        if (i) {
            details.name = i.name;
            details.group = getGroupName(i);
            details.comment = i.comment;
        }
    }
    return details;
}

function renderHistoryItem(entry, state) {
    const isEarn = entry.type === 'earn';
    const entryIcon = isEarn ? 'icon-coin-stack' : 'icon-shop';
    const details = getEntryDetails(entry, state);
    const formattedDate = new Date(getCreatedAt(entry)).toLocaleDateString('ru-RU', {
        day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit'
    });
    const moneyVal = entry.moneyAmount || entry.rsdAmount;
    const moneyTag = moneyVal ? `<span class="tag tag--money-solid" style="font-size:0.75em;margin-left:0.5em;">Сумма: 💶 ${moneyVal}</span>` : '';
    const groupTag = details.group ? ` <span class="tag tag--info" style="font-size:0.75em;margin-left:0.5em;white-space:nowrap;">[${escapeHtml(details.group)}]</span>` : '';
    const commentDiv = details.comment ? `<div style="font-size:0.85em;opacity:0.8;margin-top:0.3em;">${escapeHtml(details.comment)}</div>` : '';



    return `
        <div class="history-item history-item--${entry.type}">
            <div class="history-item__icon"><span class="gamified-icon ${entryIcon}" aria-hidden="true"></span></div>
            <div class="history-item__content">
                <div class="history-item__desc" style="display:flex;flex-wrap:wrap;align-items:center;">
                    ${escapeHtml(details.name)}${groupTag}${moneyTag}
                </div>
                ${commentDiv}
                <div class="history-item__date" style="margin-top:0.3em;">${formattedDate}</div>
            </div>
            <div class="history-item__actions">
                <div class="history-item__amount">${isEarn ? '+' : '-'}${entry.amount}<span class="gamified-icon icon-coin-stack" aria-hidden="true"></span></div>
                ${state.isAdmin ? `
                <button class="history-item__delete-btn" onclick="window.app.deleteHistoryItem('${entry.id}')" title="Удалить" aria-label="Удалить">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"></path><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"></path><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"></path></svg>
                </button>
                ` : ''}
            </div>
        </div>
    `;
}

function renderMonthHeader(monthName, stats) {
    const moneySpent = stats.moneySpent > 0 ? `<span class="money">-${stats.moneySpent.toLocaleString()} 💶</span>` : '';
    return `
        <div class="history-month-header">
            <div class="month-title">${monthName}</div>
            <div class="month-stats">
                <span class="earn">+${stats.earned} <span class="gamified-icon icon-coin-stack" aria-hidden="true" style="width: 1rem; height: 1rem; vertical-align: middle;"></span></span> | <span class="spend">-${stats.spent} <span class="gamified-icon icon-coin-stack" aria-hidden="true" style="width: 1rem; height: 1rem; vertical-align: middle;"></span></span>${moneySpent ? ' | Сумма: ' + moneySpent : ''}
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
        if (emptyState && !state.isLoading) emptyState.classList.remove('hidden');
        return;
    }
    if (emptyState) emptyState.classList.add('hidden');

    const grouped = history.reduce((acc, entry) => {
        const createdAt = getCreatedAt(entry);
        if (!createdAt) return acc;
        const dateStr = typeof createdAt === 'string' ? createdAt : new Date(createdAt).toISOString();
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
        const items = grouped[monthKey].items
            .sort((a, b) => new Date(getCreatedAt(b)) - new Date(getCreatedAt(a)))
            .map(item => renderHistoryItem(item, state)).join('');
        return renderMonthHeader(name, grouped[monthKey]) + items;
    }).join('');
}
