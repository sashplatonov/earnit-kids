import { loadDataFromServer, logout, loadBaseData } from './api.js';
import { state, setState } from './state.js';
import { renderAll, renderTasks, renderShop } from './ui.js';
import { showToast, closeModal, openModal, handleConfirm } from './utils.js';
import { scheduleSave, buyItem, earnCoins, requestCoins, deleteHistoryItem, approveRequest, rejectRequest, deleteRequest } from './actions.js';
import { openTaskModal, saveTask, deleteTask, editTask, openShopModal, saveShopItem, deleteShopItem, editShopItem, openChangePinModal, saveNewPin } from './admin.js';
import { renderRules, openEditRules, saveRules } from './rules.js';

// Catalog Logic
function renderCatalog() {
    const ageInput = document.getElementById('catalog-age-filter');
    const ignoreAgeCheckbox = document.getElementById('catalog-ignore-age');
    if (!ageInput) return;

    const age = parseInt(ageInput.value) || 7;
    const ignoreAge = ignoreAgeCheckbox ? ignoreAgeCheckbox.checked : false;

    const tasksList = document.getElementById('catalog-tasks-list');
    const productsList = document.getElementById('catalog-products-list');

    if (tasksList && state.baseData.tasks) {
        const tasks = state.baseData.tasks.filter(t => ignoreAge || (age >= t.age_min && age <= t.age_max));
        tasksList.innerHTML = tasks.map(t => `
            <div class="catalog-item">
                <div class="catalog-info">
                    <span class="catalog-name">${t.name}</span>
                    <span class="catalog-meta">${t.coins} 🪙 | ${t.age_min}-${t.age_max} л.</span>
                </div>
                <button class="btn-add" onclick="window.app.addCatalogItem('task', '${t.id}')">+</button>
            </div>
        `).join('');
    }

    if (productsList && state.baseData.products) {
        const products = state.baseData.products.filter(p => ignoreAge || (age >= p.age_min && age <= p.age_max));
        productsList.innerHTML = products.map(p => `
            <div class="catalog-item">
                <div class="catalog-info">
                    <span class="catalog-name">${p.name}</span>
                    <span class="catalog-meta">${p.price} 🪙 | ${p.age_min}-${p.age_max} л.</span>
                </div>
                <button class="btn-add" onclick="window.app.addCatalogItem('product', '${p.id}')">+</button>
            </div>
        `).join('');
    }
}

function addCatalogItem(type, id) {
    const source = type === 'task' ? state.baseData.tasks : state.baseData.products;
    const item = source.find(i => i.id === id);

    if (!item) return;

    // Check for duplicates by name
    const existing = type === 'task'
        ? state.tasks.find(t => t.name === item.name)
        : state.shopItems.find(i => i.name === item.name);

    if (existing) {
        showToast('Такой ' + (type === 'task' ? 'задание' : 'товар') + ' уже есть!', 'error');
        return;
    }

    const newItem = {
        ...item,
        id: Date.now(), // New unique ID
        frequency: { limit: 1, period: 'day' } // Default frequency
    };

    // Clean up base data specific fields if needed
    delete newItem.age_min;
    delete newItem.age_max;

    if (type === 'task') {
        state.tasks.push(newItem);
        renderTasks();
        showToast('Задание добавлено', 'success');
    } else {
        state.shopItems.push(newItem);
        renderShop();
        showToast('Товар добавлен', 'success');
    }

    scheduleSave();
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
    deleteRequest,
    addCatalogItem
};

// Helper to get cookie value
function getCookie(name) {
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${name}=`);
    if (parts.length === 2) return parts.pop().split(';').shift();
}

// Initialization
document.addEventListener('DOMContentLoaded', async () => {
    // Determine role from cookie
    const role = getCookie('app_role') || 'child';

    // Load data
    const data = await loadDataFromServer();
    if (data) {
        // Load Base Data if Admin
        let baseData = { tasks: [], products: [] };
        if (data.isAdmin) {
            baseData = await loadBaseData() || baseData;
        }

        setState({
            isAdmin: data.isAdmin || false,
            role: data.isAdmin ? 'admin' : 'child',
            familyId: data.familyId || null,
            balance: data.balance || 0,
            tasks: data.tasks || [],
            shopItems: data.shop || [],
            history: data.history || [],
            requests: data.requests || [],
            baseData: baseData
        });
    } else {
        showToast('Не удалось загрузить данные с сервера', 'error');
    }

    renderAll();
    renderRules();
    // Render catalog if admin
    if (state.isAdmin) renderCatalog();

    // Show rules edit button if admin
    if (state.isAdmin) {
        const editRulesBtn = document.getElementById('edit-rules-btn');
        if (editRulesBtn) {
            editRulesBtn.classList.remove('hidden');
            editRulesBtn.parentElement.classList.remove('hidden');
            editRulesBtn.addEventListener('click', openEditRules);
        }
    }

    const rulesSave = document.getElementById('rules-save');
    if (rulesSave) rulesSave.addEventListener('click', saveRules);

    const rulesCancel = document.getElementById('rules-cancel');
    if (rulesCancel) rulesCancel.addEventListener('click', () => closeModal('rules-modal'));

    // Show catalog button if admin
    if (state.isAdmin) {
        const catBtn = document.getElementById('nav-catalog');
        if (catBtn) catBtn.classList.remove('hidden');
    }

    // Event Listeners

    // Catalog Filter
    const ageFilter = document.getElementById('catalog-age-filter');
    if (ageFilter) {
        ageFilter.addEventListener('input', renderCatalog);
        ageFilter.addEventListener('change', renderCatalog);
    }

    const ignoreAge = document.getElementById('catalog-ignore-age');
    if (ignoreAge) {
        ignoreAge.addEventListener('change', renderCatalog);
    }

    // Logout
    const logoutBtn = document.getElementById('logout-btn');
    if (logoutBtn) logoutBtn.addEventListener('click', async () => {
        if (await logout()) {
            window.location.reload();
        } else {
            showToast('Ошибка при выходе', 'error');
        }
    });

    // Change PIN
    const changePinBtn = document.getElementById('change-pin-btn');
    if (changePinBtn) changePinBtn.addEventListener('click', openChangePinModal);

    const changePinSubmit = document.getElementById('change-pin-submit');
    if (changePinSubmit) changePinSubmit.addEventListener('click', saveNewPin);

    const changePinCancel = document.getElementById('change-pin-cancel');
    if (changePinCancel) changePinCancel.addEventListener('click', () => closeModal('change-pin-modal'));

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
    if (confirmCancel) confirmCancel.addEventListener('click', () => closeModal('confirm-modal'));

    // Tabs
    document.querySelectorAll('.nav__btn').forEach(btn => {
        btn.addEventListener('click', () => {
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
