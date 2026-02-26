/** @file Super Admin frontend helper script */
import { renderFamilyDetails } from './modules/super-admin-family-details.js';
import { checkReserveStatus, handleRestore, handleCopyToReserve } from './modules/super-admin-db.js';
import { setBaseData, getBaseData, renderList, deleteItem, saveItem } from './modules/super-admin-base.js';
import { applyFamiliesFilters, getFamilyChildrenCount } from './modules/super-admin-filters.js';

let familiesData = [];
const familiesViewState = {
    status: 'all',
    sort: 'created',
    search: ''
};

// Tab switching
document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
        document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
        btn.classList.add('active');
        document.getElementById('tab-' + btn.dataset.tab).classList.add('active');
        if (btn.dataset.tab === 'database') checkReserveStatus();
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
    if (file && confirm('ВНИМАНИЕ! Это действие ЗАМЕНИТ базу данных. Продолжить?')) {
        handleRestore(file);
    }
    e.target.value = '';
});
document.getElementById('pg-copy-reserve-btn').addEventListener('click', handleCopyToReserve);

// Logout
document.getElementById('logout-btn').addEventListener('click', async () => {
    await fetch('/api/logout', { method: 'POST' });
    window.location.reload();
});

// Families
async function loadFamilies() {
    try {
        const res = await fetch('/api/super/families');
        if (res.ok) {
            const data = await res.json();
            familiesData = data.families || [];
            renderFamilies();
        }
    } catch (err) { console.error('Error:', err); }
}

function findLatestFamily(families) {
    let latest = null;
    for (const family of families) {
        if (!latest || new Date(family.created_at) > new Date(latest.created_at)) {
            latest = family;
        }
    }
    return latest;
}

function updateStats(families) {
    document.getElementById('total-families').textContent = families.length;
    const latest = findLatestFamily(families);
    document.getElementById('latest-family').textContent = latest ? `${latest.email || latest.id} (${new Date(latest.created_at).toLocaleString('ru-RU')})` : '-';
}

function getFamilyStatusBadge(isBlocked) {
    return isBlocked ? '<span style="color:#ef4444; font-weight:700;">ЗАБЛОКИРОВАНА</span>' : '<span style="color:#16a34a; font-weight:700;">АКТИВНА</span>';
}

function getFamilyBlockButtonLabel(isBlocked) {
    return isBlocked ? '🔓' : '🔒';
}

function getFamilyBlockButtonClass(isBlocked) {
    return isBlocked ? 'unblock' : '';
}

function buildFamilyRowHtml(f) {
    return `
            <td style="opacity:0.5" class="hide-mobile">#${f.id}</td>
            <td><strong>${f.email || f.id}</strong></td>
            <td class="hide-mobile">${f.email || '-'}</td>
            <td class="hide-mobile"><code>${f.admin_password || 'N/A'}</code></td>
            <td class="hide-mobile"><code>${f.admin_password || 'N/A'}</code></td>
            <td class="hide-mobile">${f.tasksCount || 0} <span style="opacity:0.7;">(👧 ${getFamilyChildrenCount(f)})</span></td>
            <td class="hide-mobile">${f.shopCount || 0}</td>
            <td class="hide-mobile">${f.monthly_limit || 10000} 🪙</td>
            <td>${getFamilyStatusBadge(f.isBlocked)}</td>
            <td class="hide-mobile">${new Date(f.created_at).toLocaleDateString('ru-RU')}</td>
            <td class="hide-mobile" style="font-size:0.9rem">${f.last_activity ? new Date(f.last_activity).toLocaleString('ru-RU') : '-'}</td>
            <td>
                <div style="display:flex; gap:4px">
                    <button class="view-btn" onclick="viewFamily('${f.id}')">👁️</button>
                    <button class="block-btn ${getFamilyBlockButtonClass(f.isBlocked)}" onclick="toggleBlock('${f.id}', ${!f.isBlocked})">${getFamilyBlockButtonLabel(f.isBlocked)}</button>
                </div>
            </td>`;
}

function renderFamilies() {
    const tbody = document.getElementById('families-tbody');
    const loading = document.getElementById('loading');
    const table = document.getElementById('families-table');
    tbody.innerHTML = '';
    if (familiesData.length === 0) {
        loading.textContent = 'Нет семей';
        loading.style.display = 'block';
        table.style.display = 'none';
        return;
    }
    const visibleFamilies = applyFamiliesFilters([...familiesData], familiesViewState);
    loading.style.display = visibleFamilies.length ? 'none' : 'block';
    loading.textContent = visibleFamilies.length ? '' : 'По текущим фильтрам семьи не найдены';
    table.style.display = visibleFamilies.length ? 'table' : 'none';
    updateStats(visibleFamilies);

    for (const f of visibleFamilies) {
        const tr = document.createElement('tr');
        tr.innerHTML = buildFamilyRowHtml(f);
        tbody.appendChild(tr);
    }
}

function setupFamiliesControls() {
    const statusChips = document.querySelectorAll('[data-filter-status]');
    const sortChips = document.querySelectorAll('[data-sort]');
    const searchInput = document.getElementById('families-search');

    statusChips.forEach((chip) => {
        chip.addEventListener('click', () => {
            familiesViewState.status = chip.dataset.filterStatus || 'all';
            statusChips.forEach(node => node.classList.toggle('active', node === chip));
            renderFamilies();
        });
    });

    sortChips.forEach((chip) => {
        chip.addEventListener('click', () => {
            familiesViewState.sort = chip.dataset.sort || 'created';
            sortChips.forEach(node => node.classList.toggle('active', node === chip));
            renderFamilies();
        });
    });

    if (searchInput) {
        searchInput.addEventListener('input', () => {
            familiesViewState.search = searchInput.value.trim();
            renderFamilies();
        });
    }
}

// Base Data
async function loadBaseData() {
    try {
        const res = await fetch('/api/super/base-data');
        if (res.ok) {
            const data = await res.json();
            setBaseData(data);
            renderBase();
        }
    } catch (err) { console.error('Error:', err); }
}

function renderBase() {
    const data = getBaseData();
    renderList('tasks', data.tasks, document.getElementById('base-tasks-list'));
    renderList('products', data.products, document.getElementById('base-products-list'));
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
window.viewFamily = async (familyId) => {
    const modal = document.getElementById('family-modal');
    modal.classList.add('active');
    document.getElementById('modal-title').textContent = 'Загрузка...';
    document.getElementById('modal-body').innerHTML = '<div class="loading">Загрузка данных...</div>';
    try {
        const res = await fetch(`/api/super/family/${familyId}/data`);
        if (res.ok) renderFamilyDetails(await res.json());
        else document.getElementById('modal-body').innerHTML = '<p style="color: red;">Ошибка загрузки</p>';
    } catch (err) { document.getElementById('modal-body').innerHTML = '<p style="color: red;">Ошибка связи</p>'; }
};

document.getElementById('modal-close').addEventListener('click', () => document.getElementById('family-modal').classList.remove('active'));
document.getElementById('family-modal').addEventListener('click', (e) => { if (e.target.id === 'family-modal') document.getElementById('family-modal').classList.remove('active'); });

window.toggleBlock = async (familyId, shouldBlock) => {
    if (!confirm(shouldBlock ? 'Заблокировать?' : 'Разблокировать?')) return;
    try {
        const res = await fetch(`/api/super/family/${familyId}/block`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ isBlocked: shouldBlock })
        });
        if (res.ok) loadFamilies(); else alert('Ошибка');
    } catch (err) { alert('Ошибка связи'); }
};

window.copyMagicLink = (token) => {
    if (!token) return alert('Token missing');
    navigator.clipboard.writeText(`${window.location.origin}/login-child/${token}`).then(() => alert('Link copied')).catch(() => alert('Failed to copy'));
};

window.regenerateToken = async (familyId, childId) => {
    if (!confirm('Regenerate link?')) return;
    try {
        const url = childId ? `/api/super/child/${childId}/regenerate-token` : `/api/super/family/${familyId}/regenerate-token`;
        const res = await fetch(url, { method: 'POST' });
        if (res.ok) { alert('Token regenerated'); loadFamilies(); } else alert('Failed');
    } catch (err) { alert('Error'); }
};

setupFamiliesControls();
loadFamilies(); loadBaseData();
