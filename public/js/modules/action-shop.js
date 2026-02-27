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

export function buyItem(itemId) {
    const item = state.shopItems.find(i => i.id == itemId);
    const actingId = getActingChildId();
    if (!item || !actingId) return showToast(!item ? 'Item not found' : 'Select child', 'error');

    if (state.balance < item.price) return showToast('Недостаточно монет!', 'error');

    const mLimit = item.moneyLimit || item.money_limit || 0;
    const err = checkLimits(item, mLimit, actingId);
    if (err) return showToast(err, 'error');

    if (state.isAdmin) {
        showConfirm('Подтвердите покупку', `Купить "${item.name}" за ${item.price} мон.?`, () => applyPurchase(item, actingId, mLimit));
    } else {
        showConfirm('Отправить заявку?', `"${item.name}" за ${item.price} мон.`, () => sendPurchaseRequest(item, actingId, mLimit));
    }
}
