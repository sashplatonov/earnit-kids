/** @file Action History frontend UI module */
import { state, setState } from './state.js';
import { renderAll } from './ui.js';
import { showToast, showConfirm } from './utils.js';
import { scheduleSave, getActingChildId, updateBalanceLocally } from './action-helpers.js';

export function deleteHistoryItem(id) {
    showConfirm('Удаление записи', 'Удалить эту запись из истории?', {
        onConfirm: () => {
            const entry = state.history.find(h => h.id == id);
            if (entry) {
                const delta = entry.type === 'earn' ? -(entry.amount || 0) : (entry.amount || 0);
                updateBalanceLocally(entry.childId || getActingChildId(), delta);
            }
            state.history = state.history.filter(h => h.id != id);
            scheduleSave();
            renderAll();
            showToast('Запись удалена', 'info');
        }
    });
}
