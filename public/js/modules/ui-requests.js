import { escapeHtml } from './utils.js';

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

    return `
        <div class="history-item ${getRequestRowClass(req)}">
            <div class="history-item__icon">${getRequestIcon(req)}</div>
            <div class="history-item__content">
                <div class="history-item__desc">
                    ${escapeHtml(req.taskName)}
                    ${groupTag}
                </div>
                ${commentHtml}
                <div class="history-item__date">Ожидает подтверждения</div>
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

function renderChildRequests({ pending, list, empty }) {
    list.innerHTML = pending.length ? pending.sort((a, b) => b.id - a.id).map(renderMyRequest).join('') : '';
    if (empty) empty.classList.toggle('hidden', pending.length > 0);
    document.getElementById('requests-section')?.querySelector('.admin-only')?.classList.add('hidden');
}

export function renderRequestsUI(state) {
    const incomingList = document.getElementById('incoming-requests-list');
    const incomingEmpty = document.getElementById('incoming-requests-empty');
    const myList = document.getElementById('my-requests-list');
    const myEmpty = document.getElementById('my-requests-empty');

    const pending = state.requests.filter(r => r.status === 'pending');
    updateBadge(pending.length);

    if (!incomingList || !myList) return;

    if (state.isAdmin) {
        myList.innerHTML = '';
        if (myEmpty) myEmpty.classList.add('hidden');
        renderAdminRequests({ pending, state, list: incomingList, empty: incomingEmpty });
    } else {
        if (incomingEmpty) incomingEmpty.classList.add('hidden');
        renderChildRequests({ pending, list: myList, empty: myEmpty });
    }
}
