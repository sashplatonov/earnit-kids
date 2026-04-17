/** @file Action Requests frontend UI module */
import { state } from './state.js';
import { renderAll } from './ui.js';
import { approveRequestOnServer, rejectRequestOnServer, deleteRequestOnServer } from './api.js';
import { showToast, showConfirm, showMobileEventNotification } from './utils.js';
import { applyServerFamilyData, checkDailyCoinLimit, checkFrequency, checkLimits, flushPendingSave } from './action-helpers.js';
import { triggerCoinBurst } from './motion-feedback.js';

function verifyPurchaseLimits(req, item, callback) {
    if (!item) return callback();
    const err = checkLimits(item, req.moneyAmount || 0, {
        childIdOverride: req.childId,
        excludeRequestId: req.id
    });
    if (err) {
        const warningMsg = `${err}<br><br>Списать ${req.coins} <span class="gamified-icon icon-coin-stack" aria-hidden="true" style="width: 1.1rem; height: 1.1rem; vertical-align: middle;"></span>?`;
        showConfirm('Лимит превышен', warningMsg, { onConfirm: callback });
    } else {
        callback();
    }
}

async function commitRequestMutation(executor) {
    await flushPendingSave();
    const result = await executor();
    if (!result.success || !result.data) {
        showToast(result.error || 'Не удалось обновить заявку', 'error');
        return false;
    }

    applyServerFamilyData(result.data, { currentChildId: state.currentChildId });
    renderAll();
    return true;
}

function handleApprovePurchase(req) {
    const child = state.children.find(c => c.id == req.childId);
    if (child && child.balance < req.coins) return showToast(`Недостаточно монет у ребенка (${child.name})`, 'error');

    const item = state.shopItems.find(i => i.id == (req.itemId || req.taskId));
    
    verifyPurchaseLimits(req, item, () => {
        void commitRequestMutation(() => approveRequestOnServer(req.id, state.currentChildId))
            .then((success) => {
                if (success) {
                    triggerCoinBurst();
                }
            });
    });
}

function handleApproveTask(req) {
    const task = state.tasks.find(t => t.id == req.taskId && (!t.childId || t.childId == req.childId));
    const warnings = [];
    
    if (task) {
        const freqErr = checkFrequency(task, req.childId, req.id);
        if (freqErr) warnings.push(freqErr);
    }
    
    const dailyErr = checkDailyCoinLimit(req.childId, req.coins, req.id);
    if (dailyErr) warnings.push(dailyErr);

    const apply = () => {
        void commitRequestMutation(() => approveRequestOnServer(req.id, state.currentChildId))
            .then((success) => {
                if (!success) {
                    return;
                }
                showMobileEventNotification(`Заявка подтверждена: +${req.coins} мон.`, 'success', 'Заявка подтверждена');
                triggerCoinBurst();
            });
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
    if (isPurchase) {
        handleApprovePurchase(req);
        return;
    }

    handleApproveTask(req);
}

export function rejectRequest(reqId) {
    const req = state.requests.find(r => r.id == reqId);
    if (!req) return;

    void commitRequestMutation(() => rejectRequestOnServer(req.id, state.currentChildId))
        .then((success) => {
            if (success) {
                showToast('Заявка отклонена', 'info');
            }
        });
}

export function deleteRequest(reqId) {
    showConfirm('Удалить заявку?', 'Вы уверены, что хотите безвозвратно удалить заявку?', {
        onConfirm: () => {
            void commitRequestMutation(() => deleteRequestOnServer(reqId, state.currentChildId));
        }
    });
}
