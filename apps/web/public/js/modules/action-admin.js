/** @file Action Admin frontend UI module */
import { state } from './state.js';
import { adjustBalanceOnServer } from './api.js';
import { renderAll } from './ui.js';
import { showToast, showMobileEventNotification } from './utils.js';
import { applyServerFamilyData, flushPendingSave } from './action-helpers.js';

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

    void (async () => {
        await flushPendingSave();
        const result = await adjustBalanceOnServer(state.currentChildId, amount, description);
        if (!result.success || !result.data) {
            showToast(result.error || 'Не удалось изменить баланс', 'error');
            return;
        }

        applyServerFamilyData(result.data, { currentChildId: state.currentChildId });
        renderAll();
        showMobileEventNotification(`${amount > 0 ? 'Начислено' : 'Списано'}: ${Math.abs(amount)} мон.`, 'success', 'Balance updated');
    })();
}
