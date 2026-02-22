import { state } from './state.js';
import { renderAll, renderRequests } from './ui.js';
import { showToast, showMobileEventNotification } from './utils.js';
import { scheduleSave, addHistoryEntry, checkLimits, checkDailyCoinLimit, updateBalanceLocally } from './action-helpers.js';

function verifyPurchaseLimits(req, item) {
    if (!item) return true;
    const err = checkLimits(item, req.moneyAmount || 0, req.childId);
    if (err && !confirm(`${err}. Все равно подтвердить?`)) return false;
    return true;
}

function handleApprovePurchase(req) {
    const child = state.children.find(c => c.id == req.childId);
    if (child && child.balance < req.coins) return showToast(`Недостаточно монет у ребенка (${child.name})`, 'error');

    const item = state.shopItems.find(i => i.id == (req.itemId || req.taskId));
    if (!verifyPurchaseLimits(req, item)) return;

    updateBalanceLocally(req.childId, -req.coins);
    addHistoryEntry({
        type: 'spend',
        amount: req.coins,
        description: req.taskName || 'Покупка',
        group: item ? item.group : undefined,
        comment: item ? item.comment : undefined,
        relatedId: req.itemId || req.taskId,
        moneyAmount: req.moneyAmount || 0,
        childIdOverride: req.childId
    });
    state.requests = state.requests.filter(r => r.id != req.id);
    scheduleSave();
    renderAll();
}

export function approveRequest(reqId) {
    const req = state.requests.find(r => r.id == reqId);
    if (!req) return;

    if (req.requestType === 'shop_purchase') {
        handleApprovePurchase(req);
        return;
    }

    const err = checkDailyCoinLimit(req.childId, req.coins);
    if (err && !confirm(`${err}. Все равно начислить?`)) return;

    updateBalanceLocally(req.childId, req.coins);
    const src = state.tasks.find(t => t.id == req.taskId && (!req.childId || t.childId == req.childId));
    const desc = src ? src.name : req.taskName;

    addHistoryEntry({
        type: 'earn',
        amount: req.coins,
        description: desc,
        group: src ? src.group : undefined,
        comment: src ? src.comment : undefined,
        relatedId: req.taskId,
        childIdOverride: req.childId
    });
    state.requests = state.requests.filter(r => r.id != reqId);
    scheduleSave();
    renderAll();
    showMobileEventNotification(`Заявка подтверждена: +${req.coins} 🪙`, 'success', 'Заявка подтверждена');
}

export function rejectRequest(reqId) {
    if (!confirm('Отклонить заявку?')) return;
    state.requests = state.requests.filter(r => r.id != reqId);
    scheduleSave();
    renderRequests();
    showToast('Заявка отклонена', 'info');
}

export function deleteRequest(reqId) {
    if (!confirm('Удалить заявку?')) return;
    state.requests = state.requests.filter(r => r.id != reqId);
    scheduleSave();
    renderRequests();
}
