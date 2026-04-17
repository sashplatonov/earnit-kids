/** @file Action History frontend UI module */
import { state } from './state.js';
import { deleteHistoryEntryOnServer } from './api.js';
import { renderAll } from './ui.js';
import { showToast, showConfirm } from './utils.js';
import { applyServerFamilyData, flushPendingSave } from './action-helpers.js';

export function deleteHistoryItem(id) {
    showConfirm('Удаление записи', 'Удалить эту запись из истории?', {
        onConfirm: () => {
            void (async () => {
                await flushPendingSave();
                const result = await deleteHistoryEntryOnServer(id, state.currentChildId);
                if (!result.success || !result.data) {
                    showToast(result.error || 'Не удалось удалить запись', 'error');
                    return;
                }

                applyServerFamilyData(result.data, { currentChildId: state.currentChildId });
                renderAll();
                showToast('Запись удалена', 'info');
            })();
        }
    });
}
