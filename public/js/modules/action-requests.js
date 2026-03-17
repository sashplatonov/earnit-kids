/** @file Action Requests frontend UI module */
import { state } from './state.js';
import { renderAll, renderRequests } from './ui.js';
import { showToast, showConfirm, showMobileEventNotification, escapeHtml } from './utils.js';
import { scheduleSave, addHistoryEntry, checkLimits, checkFrequency, checkDailyCoinLimit, updateBalanceLocally } from './action-helpers.js';
import { triggerCoinBurst } from './motion-feedback.js';

function verifyPurchaseLimits(req, item, callback) {
    if (!item) return callback();
    const err = checkLimits(item, req.moneyAmount || 0, req.childId, req.id);
    if (err) {
        const warningMsg = `${err}<br><br>Списать ${req.coins} <span class="gamified-icon icon-coin-stack" aria-hidden="true" style="width: 1.1rem; height: 1.1rem; vertical-align: middle;"></span>?`;
        showConfirm('Лимит превышен', warningMsg, { onConfirm: callback });
    } else {
        callback();
    }
}

function finalizeRequest(req, status) {
    if (!req) return null;
    const updated = { ...req, status, resolvedAt: new Date().toISOString() };
    state.requests = state.requests.map(r => (r.id == req.id ? updated : r));
    scheduleSave();
    return updated;
}

function handleApprovePurchase(req) {
    const child = state.children.find(c => c.id == req.childId);
    if (child && child.balance < req.coins) return showToast(`Недостаточно монет у ребенка (${child.name})`, 'error');

    const item = state.shopItems.find(i => i.id == (req.itemId || req.taskId));
    
    verifyPurchaseLimits(req, item, () => {
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
        finalizeRequest(req, 'approved');
        renderAll();
        triggerCoinBurst();
    });
}

function handleApproveTask(req) {
    const task = state.tasks.find(t => t.id == req.taskId && (!req.childId || t.childId == req.childId));
    const warnings = [];
    
    if (task) {
        const freqErr = checkFrequency(task, req.childId, req.id);
        if (freqErr) warnings.push(freqErr);
    }
    
    const dailyErr = checkDailyCoinLimit(req.childId, req.coins);
    if (dailyErr) warnings.push(dailyErr);

    const apply = () => {
        updateBalanceLocally(req.childId, req.coins);
        const desc = task ? task.name : req.taskName;

        addHistoryEntry({
            type: 'earn',
            amount: req.coins,
            description: desc,
            group: task ? task.group : undefined,
            comment: task ? task.comment : undefined,
            relatedId: req.taskId,
            childIdOverride: req.childId
        });
        finalizeRequest(req, 'approved');
        renderAll();
        showMobileEventNotification(`Заявка подтверждена: +${req.coins} мон.`, 'success', 'Заявка подтверждена');
        triggerCoinBurst();
    };

    if (warnings.length > 0) {
        const warningMsg = `${warnings.join('. ')}<br><br>Начислить ${req.coins} <span class="gamified-icon icon-coin-stack" aria-hidden="true" style="width: 1.1rem; height: 1.1rem; vertical-align: middle;"></span>?`;
        showConfirm('Лимиты превышены', warningMsg, { onConfirm: apply });
    } else {
        apply();
    }
}

export function approveRequest(reqId) {
    const req = state.requests.find(r => r.id == reqId);
    if (!req) return;

    const isPurchase = req.requestType === 'shop_purchase';
    const title = isPurchase ? 'Подтвердить покупку?' : 'Подтвердить выполнение?';
    const msg = `Подтвердить "${escapeHtml(req.taskName || 'Заявка')}" за ${req.coins} <span class="gamified-icon icon-coin-stack" aria-hidden="true" style="width: 1.1rem; height: 1.1rem; vertical-align: middle;"></span>?`;

    showConfirm(title, msg, {
        onConfirm: () => {
            if (isPurchase) {
                handleApprovePurchase(req);
            } else {
                handleApproveTask(req);
            }
        }
    });
}

export function rejectRequest(reqId) {
    const req = state.requests.find(r => r.id == reqId);
    if (!req) return;

    const msg = `Вы уверены, что хотите отклонить заявку "${escapeHtml(req.taskName || 'Заявка')}" за ${req.coins} <span class="gamified-icon icon-coin-stack" aria-hidden="true" style="width: 1.1rem; height: 1.1rem; vertical-align: middle;"></span>?`;
    showConfirm('Отклонить заявку?', msg, {
        onConfirm: () => {
            finalizeRequest(req, 'rejected');
            renderRequests();
            showToast('Заявка отклонена', 'info');
        }
    });
}

export function deleteRequest(reqId) {
    showConfirm('Удалить заявку?', 'Вы уверены, что хотите безвозвратно удалить заявку?', {
        onConfirm: () => {
            state.requests = state.requests.filter(r => r.id != reqId);
            scheduleSave();
            renderRequests();
        }
    });
}
