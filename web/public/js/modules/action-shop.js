/** @file Action Shop frontend UI module */
import { state } from './state.js';
import { renderAll, renderRequests } from './ui.js';
import { showToast, showConfirm, showMobileEventNotification, escapeHtml } from './utils.js';
import { scheduleSave, addHistoryEntry, checkLimits, getActingChildId, updateBalanceLocally, addRequestEntry } from './action-helpers.js';
import { triggerPurchaseAnimation } from './motion-feedback.js';

function getShopLimitToastMessage(errorMessage) {
    if (!errorMessage) return 'Лимит исчерпан';
    if (errorMessage.startsWith('Месячный лимит')) return `Превышен месячный лимит. ${errorMessage}`;
    if (errorMessage.startsWith('Лимит на крупные покупки')) return `Уже была крупная покупка. ${errorMessage}`;
    if (errorMessage.startsWith('Лимит (')) return `Лимит частоты. ${errorMessage}`;
    return errorMessage;
}

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
    triggerPurchaseAnimation();
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
    triggerPurchaseAnimation();
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
    const err = checkLimits(item, mLimit, { childIdOverride: actingId });
    
    if (err) {
        if (state.isAdmin) {
            // Parent can bypass
            const msg = `${err}. Все равно ${state.isAdmin ? 'купить' : 'отправить заявку'}?`;
            return showConfirm('Лимит превышен', msg, { 
                onConfirm: () => confirmPurchase(item, actingId, { mLimit, limitWarned: true }) 
            });
        } else {
            return showToast(getShopLimitToastMessage(err), 'error');
        }
    }

    confirmPurchase(item, actingId, { mLimit });
}

function confirmPurchase(item, actingId, options = {}) {
    const { mLimit, limitWarned } = options;
    const title = limitWarned ? 'Подтвердите (лимит превышен)' : (state.isAdmin ? 'Подтвердите покупку' : 'Отправить заявку?');
    let msg = state.isAdmin ? `Купить "${escapeHtml(item.name)}" за ${item.price}` : `"${escapeHtml(item.name)}" за ${item.price}`;
    msg += ` <span class="gamified-icon icon-coin-stack" aria-hidden="true" style="width: 1.2rem; height: 1.2rem; vertical-align: middle;"></span>`;
    
    if (mLimit > 0) {
        msg += `<br><span style="font-size: 0.9em; color: var(--color-text-muted);">Лимит: 💶 ${mLimit}</span>`;
    }
    
    if (state.isAdmin) msg += '?';
    
    if (state.isAdmin) {
        showConfirm(title, msg, { onConfirm: () => applyPurchase(item, actingId, mLimit) });
    } else {
        showConfirm(title, msg, { onConfirm: () => sendPurchaseRequest(item, actingId, mLimit) });
    }
}
