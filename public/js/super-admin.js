/** @file Super Admin frontend helper script */
import { handleRestore, refreshDbPanelStatus } from './modules/super-admin-db.js';
import { setBaseData, getBaseData, renderList, deleteItem, saveItem } from './modules/super-admin-base.js';
import { initSystemPanel, activateSystemTab, deactivateSystemTab } from './modules/super-admin-system.js';
import { initFamiliesPanel } from './modules/super-admin-families.js';
import { showSuperConfirm } from './modules/super-admin-dialogs.js';

const catalogStateEls = {
    tasks: document.getElementById('tasks-state'),
    products: document.getElementById('products-state')
};
const catalogListEls = {
    tasks: document.getElementById('base-tasks-list'),
    products: document.getElementById('base-products-list')
};

// Tab switching
function handleTabActivation(tabName) {
    if (tabName === 'database') refreshDbPanelStatus();
    if (tabName === 'system') activateSystemTab();
    else deactivateSystemTab();
}

document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
        document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
        btn.classList.add('active');
        const targetTab = btn.dataset.tab;
        const content = document.getElementById('tab-' + targetTab);
        if (content) content.classList.add('active');
        handleTabActivation(targetTab);
    });
});

// DB Actions
document.getElementById('pg-backup-btn').addEventListener('click', () => {
    window.location.href = '/api/super/db-backup';
});
document.getElementById('pg-restore-btn').addEventListener('click', () => {
    document.getElementById('pg-restore-input').click();
});
document.getElementById('pg-restore-input').addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (!file) {
        e.target.value = '';
        return;
    }
    showSuperConfirm({
        title: 'Восстановить базу из файла?',
        message: 'Текущие данные будут заменены.',
        confirmText: 'Восстановить'
    }).then((confirmed) => {
        if (confirmed) handleRestore(file);
    });
    e.target.value = '';
});
// Logout
document.getElementById('logout-btn').addEventListener('click', async () => {
    await fetch('/api/logout', { method: 'POST' });
    window.location.reload();
});

function updateCatalogState(type, { status, message }) {
    const stateEl = catalogStateEls[type];
    const listEl = catalogListEls[type];
    if (!stateEl || !listEl) return;
    stateEl.textContent = message || '';
    stateEl.hidden = status === 'loaded';
    listEl.hidden = status !== 'loaded';
    stateEl.classList.remove('panel-state--loading', 'panel-state--error', 'panel-state--empty');
    if (status === 'loading') stateEl.classList.add('panel-state--loading');
    if (status === 'empty') stateEl.classList.add('panel-state--empty');
    if (status === 'error') stateEl.classList.add('panel-state--error');
}

function setCatalogLoadingStates() {
    updateCatalogState('tasks', { status: 'loading', message: 'Загрузка заданий...' });
    updateCatalogState('products', { status: 'loading', message: 'Загрузка товаров...' });
}

// Base Data
async function loadBaseData() {
    setCatalogLoadingStates();
    try {
        const res = await fetch('/api/super/base-data');
        if (res.ok) {
            const data = await res.json();
            setBaseData(data);
            renderBase();
        }
    } catch (err) {
        console.error('Error:', err);
        updateCatalogState('tasks', { status: 'error', message: 'Ошибка загрузки заданий' });
        updateCatalogState('products', { status: 'error', message: 'Ошибка загрузки товаров' });
    }
}

function renderBase() {
    const data = getBaseData();
    const tasks = data.tasks || [];
    const products = data.products || [];
    renderList('tasks', tasks, catalogListEls.tasks);
    renderList('products', products, catalogListEls.products);
    updateCatalogState('tasks', { status: tasks.length ? 'loaded' : 'empty', message: tasks.length ? '' : 'Заданий пока нет' });
    updateCatalogState('products', { status: products.length ? 'loaded' : 'empty', message: products.length ? '' : 'Товаров пока нет' });
}

function getPeriodOptions(freq) {
    const p = freq.period;
    return `
        <option value="day" ${p === 'day' ? 'selected' : ''}>В день</option>
        <option value="week" ${p === 'week' ? 'selected' : ''}>В неделю</option>
        <option value="month" ${p === 'month' ? 'selected' : ''}>В месяц</option>
        <option value="year" ${p === 'year' ? 'selected' : ''}>В год</option>`;
}

function getEditFormHtml(type, item) {
    const isT = type === 'tasks';
    const f = item.frequency || { limit: '', period: 'day' };
    const cost = item.coins || item.price || 0;
    const mL = !isT ? `<div class="input-group"><label>Денежный лимит</label><input type="number" id="edit-money-limit" value="${item.money_limit || ''}"></div>` : '';

    return `
        <div class="input-group"><label>Название</label><input type="text" id="edit-name" value="${item.name}"></div>
        <div class="input-group"><label>Группа</label><input type="text" id="edit-group" value="${item.group || item.category || ''}"></div>
        <div class="input-group"><label>${isT ? 'Награда' : 'Цена'}</label><input type="number" id="edit-cost" value="${cost}"></div>
        <div class="input-group"><label>Возраст (мин)</label><input type="number" id="edit-min" value="${item.age_min}"></div>
        <div class="input-group"><label>Возраст (макс)</label><input type="number" id="edit-max" value="${item.age_max}"></div>
        <div style="display: flex; gap: 1rem; border-top: 1px solid #eee; padding-top: 1rem; margin-top: 1rem;">
            <div class="input-group" style="flex: 1"><label>Лимит</label><input type="number" id="edit-limit" value="${f.limit}"></div>
            <div class="input-group" style="flex: 1"><label>Период</label>
                <select id="edit-period">${getPeriodOptions(f)}</select>
            </div>
        </div>
        ${mL}
        <button class="save-btn" onclick="saveBtnHandler('${type}', ${item.id ? 0 : -1})">Сохранить</button>`;
}

window.editItem = (type, index) => {
    const data = getBaseData();
    const item = index === -1 ? { name: '', age_min: 7, age_max: 18 } : (type === 'tasks' ? data.tasks[index] : data.products[index]);

    document.getElementById('edit-form-container').innerHTML = getEditFormHtml(type, item);
    // Since we extracted the template, saveBtnHandler needs the real index.
    // Patching the button's onclick directly for convenience or updating template to take index.
    const btn = document.querySelector('#edit-form-container .save-btn');
    if (btn) btn.setAttribute('onclick', `saveBtnHandler('${type}', ${index})`);

    document.getElementById('edit-modal-title').textContent = index === -1 ? 'Добавить' : 'Редактировать';
    document.getElementById('edit-modal').classList.add('active');
};

window.saveBtnHandler = async (type, index) => {
    const isTask = type === 'tasks';
    const data = getBaseData();
    const newItem = {
        id: index === -1 ? Date.now().toString() : (type === 'tasks' ? data.tasks[index].id : data.products[index].id),
        name: document.getElementById('edit-name').value,
        group: document.getElementById('edit-group').value,
        age_min: parseInt(document.getElementById('edit-min').value),
        age_max: parseInt(document.getElementById('edit-max').value),
    };
    const limit = parseInt(document.getElementById('edit-limit').value);
    newItem.frequency = limit > 0 ? { limit, period: document.getElementById('edit-period').value } : null;
    if (!isTask) {
        const ml = parseInt(document.getElementById('edit-money-limit').value);
        newItem.money_limit = ml > 0 ? ml : null;
    }
    const cost = parseInt(document.getElementById('edit-cost').value);
    if (isTask) newItem.coins = cost; else newItem.price = cost;

    if (await saveItem(type, index, newItem)) {
        document.getElementById('edit-modal').classList.remove('active');
        renderBase();
    }
};

window.addItem = (t) => editItem(t, -1);
window.deleteItem = async (t, i) => { if (await deleteItem(t, i)) renderBase(); };
window.closeEditModal = () => document.getElementById('edit-modal').classList.remove('active');

// Family View
initFamiliesPanel();
initSystemPanel();
loadBaseData();
