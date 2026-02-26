/** @file Ui Requests frontend UI module */
import { escapeHtml } from './utils.js';

const REQUEST_STATUS_LABELS = {
    pending: 'В обработке',
    approved: 'Подтверждена',
    rejected: 'Отклонена',
    expired: 'Истекла'
};
const REQUEST_STATUS_HINTS = {
    pending: 'Новые заявки, ожидающие решения',
    approved: 'Готово — выдать награду',
    rejected: 'Стоит обсудить или попробовать снова',
    expired: 'Срок действия прошёл, нужна новая заявка'
};
const REQUEST_STATUS_ORDER = ['pending', 'approved', 'rejected', 'expired'];

function getRequestTimestamp(req) {
    const dateStr = req.resolvedAt || req.date;
    return dateStr ? new Date(dateStr).getTime() : 0;
}

function buildStatusBadge(req) {
    const status = req.status || 'pending';
    const label = REQUEST_STATUS_LABELS[status] || status;
    return `<span class="tag tag--status tag--status-${status}">${label}</span>`;
}

function getRequestCounts(requests, childId) {
    const counts = { pending: 0, approved: 0, rejected: 0, expired: 0 };
    (requests || []).forEach((req) => {
        if (childId && req.childId != childId) return;
        const status = req.status || 'pending';
        if (counts[status] === undefined) return;
        counts[status] += 1;
    });
    return counts;
}

function isPurchaseRequest(req) {
    return req.requestType === 'shop_purchase';
}

function getRequestIcon(req) {
    return isPurchaseRequest(req) ? '🛒' : '📝';
}

function getRequestRowClass(req) {
    return isPurchaseRequest(req) ? 'history-item--request-purchase' : 'history-item--request-task';
}

function renderMyRequest(req) {
    const isPurchase = isPurchaseRequest(req);
    const moneyTag = (req.moneyAmount || 0) > 0 ? `<span class="tag tag--money" style="margin-left: 5px;">${req.moneyAmount}</span>` : '';
    const groupTag = req.group ? `<span class="tag" style="margin-left: 5px; opacity: 0.8;">${escapeHtml(req.group)}</span>` : '';
    const commentHtml = req.comment ? `<div class="history-item__comment" style="font-size: 0.8rem; color: var(--color-text-dim); margin-top: 2px;">${escapeHtml(req.comment)}</div>` : '';
    const status = req.status || 'pending';
    const statusBadge = buildStatusBadge(req);
    const dateText = status === 'pending'
        ? 'Ожидает подтверждения'
        : `Обновлено ${new Date(req.resolvedAt || req.date).toLocaleString()}`;

    return `
        <div class="history-item ${getRequestRowClass(req)}">
            <div class="history-item__icon">${getRequestIcon(req)}</div>
            <div class="history-item__content">
                <div class="history-item__desc">
                    ${escapeHtml(req.taskName)}
                    ${groupTag}
                </div>
                ${commentHtml}
                <div class="history-item__date">${dateText} ${statusBadge}</div>
            </div>
            <div class="history-item__amount">${isPurchase ? '-' : '+'}${req.coins} 🪙 ${moneyTag}</div>
            <div class="card__actions" style="margin-left: 10px;">
                 <button class="btn btn--danger btn--small" onclick="window.app.deleteRequest(${req.id})">🗑️</button>
            </div>
        </div>
    `;
}

function renderIncomingRequest(req, state) {
    const child = state.children.find(c => c.id == req.childId);
    const childName = child ? child.name : 'Unknown';
    const isPurchase = isPurchaseRequest(req);
    const moneyTag = (req.moneyAmount || 0) > 0 ? `<span class="tag tag--money" style="margin-left: 5px;">${req.moneyAmount}</span>` : '';

    const groupTag = req.group ? `<span class="tag tag--secondary" style="margin-left: 5px; opacity: 0.8; font-size: 0.75rem;">${escapeHtml(req.group)}</span>` : '';
    const commentHtml = req.comment ? `<div class="history-item__comment" style="font-size: 0.8rem; color: var(--color-text-dim); margin-top: 2px;">${escapeHtml(req.comment)}</div>` : '';

    return `
        <div class="history-item ${getRequestRowClass(req)}">
            <div class="history-item__icon">${getRequestIcon(req)}</div>
            <div class="history-item__content">
                <div class="history-item__desc">
                    <span class="tag" style="margin-right: 5px;">${escapeHtml(childName)}</span> 
                    ${isPurchase ? 'Покупка: ' : 'Задание: '} ${escapeHtml(req.taskName)}
                    ${groupTag}
                </div>
                ${commentHtml}
                <div class="history-item__date">${new Date(req.date).toLocaleString()}</div>
            </div>
            <div class="history-item__amount">${isPurchase ? '-' : '+'}${req.coins} 🪙 ${moneyTag}</div>
            <div class="card__actions" style="margin-left: 10px;">
                 <button class="btn btn--success btn--small" onclick="window.app.approveRequest(${req.id})">✅</button>
                 <button class="btn btn--danger btn--small" onclick="window.app.rejectRequest(${req.id})">❌</button>
            </div>
        </div>
    `;
}

function renderRequestsSummary(counts, container) {
    if (!container) return;
    const total = Object.values(counts || {}).reduce((sum, value) => sum + value, 0);
    if (!total) {
        container.innerHTML = '<div class="requests-summary__empty">Входящих заявок пока нет</div>';
        container.classList.remove('hidden');
        return;
    }

    const html = REQUEST_STATUS_ORDER.map((status) => {
        const count = counts[status];
        if (!count) return '';
        const label = REQUEST_STATUS_LABELS[status] || status;
        const hint = REQUEST_STATUS_HINTS[status] || '';
        return `
            <article class="requests-summary__item">
                <span class="requests-summary__label">${label}</span>
                <span class="tag tag--status tag--status-${status}">${count}</span>
                ${hint ? `<span class="requests-summary__hint">${hint}</span>` : ''}
            </article>
        `;
    }).filter(Boolean).join('');

    container.innerHTML = html;
    container.classList.remove('hidden');
}

function updateBadge(count) {
    const navBadge = document.getElementById('requests-counter');
    if (navBadge) {
        navBadge.textContent = count;
        navBadge.classList.toggle('hidden', count === 0);
    }
}

function renderAdminRequests({ pending, state, list, empty }) {
    list.innerHTML = pending.length ? pending.map(r => renderIncomingRequest(r, state)).join('') : '';
    if (empty) empty.classList.toggle('hidden', pending.length > 0);
    document.getElementById('requests-section')?.querySelector('.admin-only')?.classList.remove('hidden');
}

function renderChildRequests({ requests, list, empty }) {
    list.innerHTML = requests.length ? requests.map(renderMyRequest).join('') : '';
    if (empty) empty.classList.toggle('hidden', requests.length > 0);
    document.getElementById('requests-section')?.querySelector('.admin-only')?.classList.add('hidden');
}

function handleAdminQueue({ state, pending, incomingList, incomingEmpty, myList, myEmpty, activeChildId }) {
    if (!incomingList) return;
    if (myList) myList.innerHTML = '';
    if (myEmpty) myEmpty.classList.add('hidden');
    const relevantPending = activeChildId ? pending.filter(r => r.childId == activeChildId) : pending;
    renderAdminRequests({ pending: relevantPending, state, list: incomingList, empty: incomingEmpty });
}

function handleChildQueue({ state, activeChildId, incomingEmpty, myList, myEmpty, requests }) {
    if (!myList) return;
    if (incomingEmpty) incomingEmpty.classList.add('hidden');
    const myRequests = (requests || [])
        .filter(r => !activeChildId || r.childId == activeChildId)
        .sort((a, b) => getRequestTimestamp(b) - getRequestTimestamp(a))
        .slice(0, 6);
    renderChildRequests({ requests: myRequests, list: myList, empty: myEmpty });
}

export function renderRequestsUI(state) {
    const incomingList = document.getElementById('incoming-requests-list');
    const incomingEmpty = document.getElementById('incoming-requests-empty');
    const myList = document.getElementById('my-requests-list');
    const myEmpty = document.getElementById('my-requests-empty');
    const summaryEl = document.getElementById('requests-summary');
    const requests = state.requests || [];

    const activeChildId = state.isAdmin ? (state.currentChildId || null) : (state.children[0]?.id || null);
    const statusCounts = getRequestCounts(requests, activeChildId);
    renderRequestsSummary(statusCounts, summaryEl);
    const pending = requests.filter(r => r.status === 'pending');
    const badgeCount = activeChildId ? pending.filter(r => r.childId == activeChildId).length : pending.length;
    updateBadge(badgeCount);

    if (!incomingList || !myList) return;

    if (state.isAdmin) {
        handleAdminQueue({ state, pending, incomingList, incomingEmpty, myList, myEmpty, activeChildId });
    } else {
        handleChildQueue({ state, activeChildId, incomingEmpty, myList, myEmpty, requests });
    }
}
