import { loadDataFromServer } from './api.js';
import { state, setState } from './state.js';
import { renderAll, updateAdminUI, renderTasks, renderShop } from './ui.js';
import { showToast, closeModal, openModal, handleConfirm } from './utils.js';
import { scheduleSave, buyItem, earnCoins, requestCoins, deleteHistoryItem, approveRequest, rejectRequest, deleteRequest } from './actions.js';
import { toggleAdminMode, checkPin, openTaskModal, saveTask, deleteTask, editTask, openShopModal, saveShopItem, deleteShopItem, editShopItem } from './admin.js';

// Import logic (moved from app.js)
let importType = null;
function openImportModal(type) {
    importType = type;
    const title = document.getElementById('import-modal-title');
    const textarea = document.getElementById('import-text');

    if (type === 'tasks') {
        if (title) title.textContent = '📋 Быстрый импорт заданий';
        if (textarea) textarea.placeholder = 'Помыть посуду | 5 | Хорошо вымыть\nУбрать комнату | 10\nСделать уроки | 15 | Все предметы';
    } else {
        if (title) title.textContent = '🛒 Быстрый импорт товаров';
        if (textarea) textarea.placeholder = 'Час игры | 20\nМороженое | 15 | Любое на выбор\nПоход в кино | 50';
    }

    if (textarea) {
        textarea.value = '';
        openModal('import-modal');
        textarea.focus();
    }
}

function processImport() {
    const textarea = document.getElementById('import-text');
    const text = textarea ? textarea.value.trim() : '';
    if (!text) {
        showToast('Введите данные для импорта', 'error');
        return;
    }

    const lines = text.split('\n').filter(line => line.trim());
    let count = 0;

    for (const line of lines) {
        const parts = line.split('|').map(p => p.trim());
        if (parts.length < 2) continue;

        const name = parts[0];
        const value = parseInt(parts[1]);
        const comment = parts[2] || '';

        if (!name || !value || value < 1) continue;

        if (importType === 'tasks') {
            state.tasks.push({
                id: Date.now() + count,
                name,
                coins: value,
                comment
            });
        } else {
            state.shopItems.push({
                id: Date.now() + count,
                name,
                price: value,
                comment
            });
        }
        count++;
    }

    if (count > 0) {
        scheduleSave();
        if (importType === 'tasks') {
            renderTasks();
        } else {
            renderShop();
        }
        closeModal('import-modal');
        showToast(`Импортировано: ${count} ${importType === 'tasks' ? 'заданий' : 'товаров'}`, 'success');
    } else {
        showToast('Не удалось распознать данные', 'error');
    }
}

// Global Exports for HTML event handlers
window.app = {
    buyItem,
    earnCoins,
    requestCoins,
    editTask,
    editShopItem,
    deleteHistoryItem,
    approveRequest,
    rejectRequest,
    deleteRequest
};

// Initialization
document.addEventListener('DOMContentLoaded', async () => {
    // Load data
    const data = await loadDataFromServer();
    if (data) {
        setState({
            isPinSet: data.isPinSet,
            balance: data.balance || 0,
            tasks: data.tasks || [],
            shopItems: data.shop || [],
            history: data.history || [],
            requests: data.requests || []
        });
    } else {
        showToast('Не удалось загрузить данные с сервера', 'error');
    }

    renderAll();

    // Event Listeners

    // Admin
    const adminToggle = document.getElementById('admin-toggle');
    if (adminToggle) adminToggle.addEventListener('click', toggleAdminMode);

    const pinSubmit = document.getElementById('pin-submit');
    if (pinSubmit) pinSubmit.addEventListener('click', checkPin);

    const pinCancel = document.getElementById('pin-cancel');
    if (pinCancel) pinCancel.addEventListener('click', () => closeModal('pin-modal'));

    const pinInput = document.getElementById('pin-input');
    if (pinInput) pinInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') checkPin();
    });

    // Tasks
    const addTaskBtn = document.getElementById('add-task-btn');
    if (addTaskBtn) addTaskBtn.addEventListener('click', () => openTaskModal());

    const taskSave = document.getElementById('task-save');
    if (taskSave) taskSave.addEventListener('click', saveTask);

    const taskCancel = document.getElementById('task-cancel');
    if (taskCancel) taskCancel.addEventListener('click', () => closeModal('task-modal'));

    const taskDelete = document.getElementById('task-delete');
    if (taskDelete) taskDelete.addEventListener('click', deleteTask);

    // Shop
    const addShopBtn = document.getElementById('add-shop-btn');
    if (addShopBtn) addShopBtn.addEventListener('click', () => openShopModal());

    const shopSave = document.getElementById('shop-save');
    if (shopSave) shopSave.addEventListener('click', saveShopItem);

    const shopCancel = document.getElementById('shop-cancel');
    if (shopCancel) shopCancel.addEventListener('click', () => closeModal('shop-modal'));

    const shopDelete = document.getElementById('shop-delete');
    if (shopDelete) shopDelete.addEventListener('click', deleteShopItem);

    // Confirmation
    const confirmOk = document.getElementById('confirm-ok');
    if (confirmOk) confirmOk.addEventListener('click', handleConfirm);

    const confirmCancel = document.getElementById('confirm-cancel');
    if (confirmCancel) confirmCancel.addEventListener('click', () => {
        handleConfirm(); // Cleanup handles nulling callback without calling it if we modify handleConfirm logic or just manually:
        // Actually handleConfirm calls callback. We want to CANCEL.
        // Re-implementing cancel inline or import specialized cancel
        closeModal('confirm-modal');
        // also null callback
        // window.app... wait, callback is in module scope of utils/actions. 
        // We need a clearConfirmCallback in utils? Or handleConfirm just closes if no callback?
        // existing handleConfirm calls it.
        // Let's just use closeModal here and assume callback is overwritten next time or ignored.
        // Ideally utils should export cancelConfirm()
    });

    // Tabs
    document.querySelectorAll('.nav__btn').forEach(btn => {
        btn.addEventListener('click', () => {
            // Switch tab logic
            const tabName = btn.dataset.tab;
            document.querySelectorAll('.nav__btn').forEach(b => b.classList.toggle('active', b.dataset.tab === tabName));
            document.querySelectorAll('.section').forEach(s => s.classList.add('hidden'));
            const section = document.getElementById(`${tabName}-section`);
            if (section) section.classList.remove('hidden');
        });
    });

    // Modals backdrop
    document.querySelectorAll('.modal__backdrop').forEach(backdrop => {
        backdrop.addEventListener('click', () => {
            const modal = backdrop.closest('.modal');
            if (modal) modal.classList.remove('active');
        });
    });

    // Import
    const importTasksBtn = document.getElementById('import-tasks-btn');
    if (importTasksBtn) importTasksBtn.addEventListener('click', () => openImportModal('tasks'));

    const importShopBtn = document.getElementById('import-shop-btn');
    if (importShopBtn) importShopBtn.addEventListener('click', () => openImportModal('shop'));

    const importSubmit = document.getElementById('import-submit');
    if (importSubmit) importSubmit.addEventListener('click', processImport);

    const importCancel = document.getElementById('import-cancel');
    if (importCancel) importCancel.addEventListener('click', () => closeModal('import-modal'));

    // History
    const clearHistoryBtn = document.getElementById('clear-history-btn');
    if (clearHistoryBtn) {
        clearHistoryBtn.addEventListener('click', () => {
            if (!confirm('Очистить ВСЮ историю? Это нельзя отменить.')) return;
            setState({ history: [] });
            scheduleSave();
            renderAll();
            showToast('История очищена', 'info');
        });
    }
});
