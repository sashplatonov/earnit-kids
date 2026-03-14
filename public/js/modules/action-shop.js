/** @file Action Shop frontend UI module */
import { state } from './state.js';
import { renderAll, renderRequests } from './ui.js';
import { showToast, showConfirm, showMobileEventNotification } from './utils.js';
import { scheduleSave, addHistoryEntry, checkLimits, getActingChildId, updateBalanceLocally, addRequestEntry } from './action-helpers.js';
import { triggerCoinBurst } from './motion-feedback.js';

function applyPurchase(item, actingId, moneyPrice) {
    updateBalanceLocally(actingId, -item.price);
    addHistoryEntry({
        type: 'spend',
        amount: item.price,
        description: item.name,
        group: item.group,
        comment: item.comment,
        relatedId: item.id,
        moneyAmount: moneyPrice,
        childIdOverride: actingId
    });
    scheduleSave();
    renderAll();
    showMobileEventNotification(`Вы купили: ${item.name}!`, 'success', 'Balance updated');
    triggerCoinBurst();
}

function sendPurchaseRequest(item, actingId, moneyPrice) {
    addRequestEntry({
        childId: actingId,
        requestType: 'shop_purchase',
        itemId: item.id,
        taskId: item.id,
        taskName: item.name,
        coins: item.price,
        moneyAmount: moneyPrice
    });
    scheduleSave();
    renderRequests();
    document.querySelector('.nav__btn[data-tab="requests"]')?.click();
    showMobileEventNotification('Заявка на покупку отправлена', 'success', 'Новая заявка');
}

function getBuyItemError(item) {
    if (!item) return 'Товар не найден';
    if (state.children.length === 0) return 'Сначала добавьте ребенка';
    return 'Сначала выберите ребенка';
}

export function buyItem(itemId) {
    const item = state.shopItems.find(i => i.id == itemId);
    const actingId = getActingChildId();
    if (!item || !actingId) {
        return showToast(getBuyItemError(item), 'error');
    }

    if (state.balance < item.price) return showToast('Недостаточно монет!', 'error');

    const mLimit = item.moneyLimit || item.money_limit || 0;
    const err = checkLimits(item, mLimit, actingId);
    
    if (err) {
        if (state.isAdmin) {
            // Parent can bypass
            const msg = `${err}. Все равно ${state.isAdmin ? 'купить' : 'отправить заявку'}?`;
            return showConfirm('Лимит превышен', msg, { 
                onConfirm: () => confirmPurchase(item, actingId, { mLimit, limitWarned: true }) 
            });
        } else {
            // Child is blocked
            return showConfirm('Лимит исчерпан', err, { cancelLabel: 'Понятно', hideConfirm: true });
        }
    }

    confirmPurchase(item, actingId, { mLimit });
}

function confirmPurchase(item, actingId, options = {}) {
    const { mLimit, limitWarned } = options;
    const title = limitWarned ? 'Подтвердите (лимит превышен)' : (state.isAdmin ? 'Подтвердите покупку' : 'Отправить заявку?');
    const msg = state.isAdmin ? `Купить "${item.name}" за ${item.price} мон.?` : `"${item.name}" за ${item.price} мон.`;
    
    if (state.isAdmin) {
        showConfirm(title, msg, { onConfirm: () => applyPurchase(item, actingId, mLimit) });
    } else {
        showConfirm(title, msg, { onConfirm: () => sendPurchaseRequest(item, actingId, mLimit) });
    }
}
