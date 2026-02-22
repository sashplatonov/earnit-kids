import { state } from './state.js';
import { renderAll } from './ui.js';
import { showToast, showMobileEventNotification } from './utils.js';
import { scheduleSave, addHistoryEntry, updateBalanceLocally } from './action-helpers.js';

function getAwardDescription(amount) {
    const desc = prompt('Описание:', amount > 0 ? 'Бонус от родителей' : 'Списано родителями');
    return desc?.trim() || (amount > 0 ? 'Начисление' : 'Списание');
}

export function adminAwardCoins() {
    if (!state.isAdmin || !state.currentChildId) return showToast('Сначала выберите ребенка', 'error');

    const input = prompt('Количество монет (можно отрицательное):');
    if (input === null) return;
    const amount = parseInt(input);
    if (!amount) return showToast('Некорректная сумма', 'error');

    const description = getAwardDescription(amount);
    if (!description) return;

    updateBalanceLocally(state.currentChildId, amount);
    addHistoryEntry({
        type: amount > 0 ? 'earn' : 'spend',
        amount: Math.abs(amount),
        description,
        childIdOverride: state.currentChildId
    });
    scheduleSave();
    renderAll();
    showMobileEventNotification(`${amount > 0 ? 'Начислено' : 'Списано'}: ${Math.abs(amount)} 🪙`, 'success', 'Balance updated');
}
