let familiesData = [];
let baseData = { tasks: [], products: [] };

// Tab switching
document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
        document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
        btn.classList.add('active');
        document.getElementById('tab-' + btn.dataset.tab).classList.add('active');
    });
});

// Logout
document.getElementById('logout-btn').addEventListener('click', async () => {
    try {
        await fetch('/api/logout', { method: 'POST' });
        window.location.reload();
    } catch (err) {
        console.error('Logout error:', err);
    }
});

// Load families
async function loadFamilies() {
    try {
        const res = await fetch('/api/super/families');
        if (res.ok) {
            const data = await res.json();
            familiesData = data.families || [];
            renderFamilies();
        } else {
            console.error('Failed to load families');
            document.getElementById('loading').textContent = 'Ошибка загрузки';
        }
    } catch (err) {
        console.error('Error loading families:', err);
        document.getElementById('loading').textContent = 'Ошибка связи с сервером';
    }
}

// Load Base Data
async function loadBaseData() {
    try {
        const res = await fetch('/api/super/base-data');
        if (res.ok) {
            baseData = await res.json();
            renderBaseData();
        }
    } catch (err) {
        console.error('Error loading base data:', err);
    }
}

// Render families table
function renderFamilies() {
    const tbody = document.getElementById('families-tbody');
    tbody.innerHTML = '';

    if (familiesData.length === 0) {
        document.getElementById('loading').textContent = 'Нет магазинов';
        return;
    }

    document.getElementById('loading').style.display = 'none';
    document.getElementById('families-table').style.display = 'table';

    // Update stats
    document.getElementById('total-families').textContent = familiesData.length;
    const latest = familiesData.length > 0 ? familiesData.reduce((latest, f) => {
        return new Date(f.created_at) > new Date(latest.created_at) ? f : latest;
    }, familiesData[0]) : null;

    if (latest) {
        const date = new Date(latest.created_at).toLocaleString('ru-RU');
        document.getElementById('latest-family').textContent = `${latest.name} (${date})`;
    } else {
        document.getElementById('latest-family').textContent = '-';
    }

    // Render table rows
    familiesData.forEach(family => {
        const tr = document.createElement('tr');
        const createdDate = new Date(family.created_at).toLocaleDateString('ru-RU');
        const lastActivityDate = family.lastActivity ? new Date(family.lastActivity).toLocaleString('ru-RU') : '-';

        tr.innerHTML = `
            <td style="opacity:0.5">#${family.id}</td>
            <td><strong>${family.name}</strong></td>
            <td>${family.email || '-'}</td>
            <td><code>${family.adminPin || 'N/A'}</code></td>
            <td>
                <div style="display:flex; gap:0.3rem; align-items:center">
                    <button class="view-btn" style="padding:0.2rem 0.4rem; font-size:0.7rem" onclick="copyMagicLink('${family.childToken}')">Copy Link</button>
                    ${family.childToken ? '<span title="Link Exists">🔗</span>' : ''}
                </div>
            </td>
            <td>${family.tasksCount || 0}</td>
            <td>${family.shopCount || 0}</td>
            <td>${family.monthlyLimit || 2000}</td>
            <td>${family.isBlocked ? '<span style="color:red">BLOCKED</span>' : '<span style="color:green">ACTIVE</span>'}</td>
            <td>${createdDate}</td>
            <td style="font-size:0.9rem">${lastActivityDate}</td>
            <td>
                <button class="view-btn" onclick="viewFamily('${family.id}')">Просмотр</button>
                <button class="block-btn ${family.isBlocked ? 'unblock' : ''}" 
                        onclick="toggleBlock('${family.id}', ${!family.isBlocked})">
                    ${family.isBlocked ? 'Разблок.' : 'Блок.'}
                </button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

// Render Base Data
function renderBaseData() {
    renderList('tasks', baseData.tasks, document.getElementById('base-tasks-list'));
    renderList('products', baseData.products, document.getElementById('base-products-list'));
}


function renderList(type, items, container) {
    container.innerHTML = '';

    // Group by category
    const grouped = items.reduce((acc, item) => {
        const cat = item.category || 'Без категории';
        if (!acc[cat]) acc[cat] = [];
        acc[cat].push(item);
        return acc;
    }, {});

    Object.keys(grouped).sort().forEach(cat => {
        const catHeader = document.createElement('h3');
        catHeader.className = 'grid-category-header';
        catHeader.textContent = cat;
        container.appendChild(catHeader);

        grouped[cat].forEach(item => {
            // Find original index in baseData[type]
            const originalIndex = baseData[type].findIndex(i => i.id === item.id);
            const card = document.createElement('div');
            const freqText = item.frequency ? ` (${item.frequency.limit}/${item.frequency.period})` : '';
            const moneyLimitText = item.money_limit ? ` | Limit: ${item.money_limit} 🪙` : '';
            card.className = 'item-card';
            card.innerHTML = `
                <div class="item-header">
                    <span>${item.name}</span>
                </div>
                <div class="item-meta" style="color: #6366f1; font-weight: 600;">
                    ${item.coins || item.price} 🪙 ${freqText}${moneyLimitText}
                </div>
                <div class="item-meta">
                    Возраст: ${item.age_min}-${item.age_max} лет
                </div>
                <div class="item-actions">
                    <button class="btn-sm btn-edit" onclick="editItem('${type}', ${originalIndex})">✏️</button>
                    <button class="btn-sm btn-del" onclick="deleteItem('${type}', ${originalIndex})">🗑️</button>
                </div>
            `;
            container.appendChild(card);
        });
    });
}

// Edit Item
window.editItem = (type, index) => {
    const item = index === -1 ? { name: '', age_min: 7, age_max: 18 } : (type === 'tasks' ? baseData.tasks[index] : baseData.products[index]);
    const isTask = type === 'tasks';

    const html = `
        <div class="input-group">
            <label>Название</label>
            <input type="text" id="edit-name" value="${item.name}">
        </div>
        <div class="input-group">
            <label>Категория</label>
            <input type="text" id="edit-category" value="${item.category || ''}" placeholder="Напр: Дом, Учеба...">
        </div>
        <div class="input-group">
            <label>${isTask ? 'Награда (монеты)' : 'Цена (монеты)'}</label>
            <input type="number" id="edit-cost" value="${item.coins || item.price || 0}">
        </div>
        <div class="input-group">
            <label>Возраст (мин)</label>
            <input type="number" id="edit-min" value="${item.age_min}">
        </div>
        <div class="input-group">
            <label>Возраст (макс)</label>
            <input type="number" id="edit-max" value="${item.age_max}">
        </div>
        <div style="display: flex; gap: 1rem; border-top: 1px solid #eee; padding-top: 1rem; margin-top: 1rem;">
            <div class="input-group" style="flex: 1">
                <label>Лимит (раз)</label>
                <input type="number" id="edit-limit" value="${item.frequency ? item.frequency.limit : ''}" placeholder="Без лимита">
            </div>
            <div class="input-group" style="flex: 1">
                <label>Период</label>
                <select id="edit-period">
                    <option value="day" ${(item.frequency && item.frequency.period === 'day') ? 'selected' : ''}>В день</option>
                    <option value="week" ${(item.frequency && item.frequency.period === 'week') ? 'selected' : ''}>В неделю</option>
                    <option value="month" ${(item.frequency && item.frequency.period === 'month') ? 'selected' : ''}>В месяц</option>
                </select>
            </div>
        </div>
        ${!isTask ? `
        <div class="input-group">
            <label>Денежный лимит (монеты)</label>
            <input type="number" id="edit-money-limit" value="${item.money_limit || ''}" placeholder="Без лимита">
        </div>
        ` : ''}
        <button class="save-btn" onclick="saveItem('${type}', ${index})">Сохранить</button>
    `;

    document.getElementById('edit-form-container').innerHTML = html;
    document.getElementById('edit-modal-title').textContent = index === -1 ? 'Добавить' : 'Редактировать';
    document.getElementById('edit-modal').classList.add('active');
};

window.addItem = (type) => editItem(type, -1);

window.saveItem = async (type, index) => {
    const isTask = type === 'tasks';
    const newItem = {
        id: index === -1 ? Date.now().toString() : (type === 'tasks' ? baseData.tasks[index].id : baseData.products[index].id),
        name: document.getElementById('edit-name').value,
        category: document.getElementById('edit-category').value,
        age_min: parseInt(document.getElementById('edit-min').value),
        age_max: parseInt(document.getElementById('edit-max').value),
    };

    const limit = parseInt(document.getElementById('edit-limit').value);
    const period = document.getElementById('edit-period').value;
    if (limit > 0) {
        newItem.frequency = { limit, period };
    } else {
        newItem.frequency = null;
    }

    if (!isTask) {
        const moneyLimit = parseInt(document.getElementById('edit-money-limit').value);
        if (moneyLimit > 0) newItem.money_limit = moneyLimit;
        else newItem.money_limit = null;
    }

    const cost = parseInt(document.getElementById('edit-cost').value);
    if (isTask) newItem.coins = cost;
    else newItem.price = cost;

    if (index === -1) {
        baseData[type].push(newItem);
    } else {
        baseData[type][index] = { ...baseData[type][index], ...newItem };
    }

    await saveBaseDataToServer();
    closeEditModal();
    renderBaseData();
};

window.deleteItem = async (type, index) => {
    if (confirm('Удалить?')) {
        baseData[type].splice(index, 1);
        await saveBaseDataToServer();
        renderBaseData();
    }
};

async function saveBaseDataToServer() {
    try {
        await fetch('/api/super/base-data', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(baseData)
        });
    } catch (err) {
        alert('Ошибка сохранения');
    }
}

window.closeEditModal = () => {
    document.getElementById('edit-modal').classList.remove('active');
};

// View family details
window.viewFamily = async (familyId) => {
    const modal = document.getElementById('family-modal');
    const modalTitle = document.getElementById('modal-title');
    const modalBody = document.getElementById('modal-body');

    modal.classList.add('active');
    modalTitle.textContent = 'Загрузка...';
    modalBody.innerHTML = '<div class="loading">Загрузка данных...</div>';

    try {
        const res = await fetch(`/api/super/family/${familyId}/data`);
        if (res.ok) {
            const data = await res.json();
            renderFamilyDetails(data);
        } else {
            modalBody.innerHTML = '<p style="color: red;">Ошибка загрузки данных</p>';
        }
    } catch (err) {
        console.error('Error loading family data:', err);
        modalBody.innerHTML = '<p style="color: red;">Ошибка связи с сервером</p>';
    }
};

// Render family details in modal
function renderFamilyDetails(familyData) {
    const modalTitle = document.getElementById('modal-title');
    const modalBody = document.getElementById('modal-body');

    modalTitle.textContent = familyData.familyInfo.name;

    let html = `
        <div class="detail-grid">
            <div class="detail-item">
                <strong>ID семьи</strong>
                <div>#${familyData.familyId}</div>
            </div>
            <div class="detail-item">
                <strong>Дата создания</strong>
                <div>${new Date(familyData.familyInfo.created_at).toLocaleString('ru-RU')}</div>
            </div>
            <div class="detail-item">
                <strong>Email</strong>
                <div>${familyData.familyInfo.email || '-'}</div>
            </div>
            <div class="detail-item">
                <strong>Баланс</strong>
                <div>${familyData.data.balance} 🪙</div>
            </div>
            <div class="detail-item">
                <strong>Лимит (мес)</strong>
                <div>${familyData.familyInfo.monthly_limit || 2000}</div>
            </div>
            <div class="detail-item" style="grid-column: span 2">
                <strong>Magic Link (Ребенок)</strong>
                <div style="display:flex; gap:0.5rem; margin-top:0.3rem">
                    <input type="text" readonly value="${window.location.origin}/login-child/${familyData.familyInfo.child_token}" style="flex:1; font-size:0.8rem" id="modal-magic-link">
                    <button class="view-btn" onclick="copyMagicLink('${familyData.familyInfo.child_token}')">Copy</button>
                    <button class="block-btn" style="background:#f59e0b" onclick="regenerateToken('${familyData.familyId}')">Refresh</button>
                </div>
            </div>
        </div>

        <h3>📋 Задания (${familyData.data.tasks.length})</h3>
        <table>
            <thead>
                <tr>
                    <th>Название</th>
                    <th>Награда</th>
                </tr>
            </thead>
            <tbody>
                ${familyData.data.tasks.map(task => `
                    <tr>
                        <td>${task.name}</td>
                        <td>${task.coins} 🪙</td>
                    </tr>
                `).join('')}
            </tbody>
        </table>

        <h3>🏪 Магазин (${familyData.data.shop.length})</h3>
        <table>
            <thead>
                <tr>
                    <th>Название</th>
                    <th>Цена</th>
                </tr>
            </thead>
            <tbody>
                ${familyData.data.shop.map(item => {
        const freqText = item.frequency ? `<br><small>${item.frequency.limit}/${item.frequency.period}</small>` : '';
        const limitText = item.money_limit ? `<br><small>Limit: ${item.money_limit}🪙</small>` : '';
        return `
                    <tr>
                        <td>${item.name}${freqText}${limitText}</td>
                        <td>${item.price} 🪙</td>
                    </tr>
                `;
    }).join('')}
            </tbody>
        </table>

        <h3>📜 История (${familyData.data.history.length})</h3>
        ${familyData.data.history.length === 0 ? '<p>Нет записей</p>' : `
            <table>
                <tbody>
                    ${familyData.data.history.slice(-10).reverse().map(h => `
                        <tr>
                            <td>${new Date(h.timestamp).toLocaleString('ru-RU')}</td>
                            <td>${h.action}</td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        `}
    `;

    modalBody.innerHTML = html;
}

// Close modal
document.getElementById('modal-close').addEventListener('click', () => {
    document.getElementById('family-modal').classList.remove('active');
});

// Close modal on outside click
document.getElementById('family-modal').addEventListener('click', (e) => {
    if (e.target.id === 'family-modal') {
        document.getElementById('family-modal').classList.remove('active');
    }
});

// Initialize
loadFamilies();
loadBaseData();

async function toggleBlock(familyId, shouldBlock) {
    if (!confirm(shouldBlock ? 'Заблокировать магазин?' : 'Разблокировать магазин?')) return;

    try {
        const res = await fetch(`/api/super/family/${familyId}/block`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ isBlocked: shouldBlock })
        });
        if (res.ok) {
            // Reload list
            loadFamilies();
        } else {
            alert('Ошибка при обновлении статуса');
        }
    } catch (err) {
        console.error('Error:', err);
        alert('Ошибка связи с сервером');
    }
}

window.copyMagicLink = (token) => {
    if (!token) return alert('Token missing');
    const link = `${window.location.origin}/login-child/${token}`;
    navigator.clipboard.writeText(link).then(() => {
        alert('Link copied to clipboard');
    }).catch(err => {
        console.error('Failed to copy link:', err);
        alert('Failed to copy. See console.');
    });
};

window.regenerateToken = async (familyId) => {
    if (!confirm('Regenerate child link? Old link will stop working.')) return;
    try {
        const res = await fetch(`/api/super/family/${familyId}/regenerate-token`, { method: 'POST' });
        if (res.ok) {
            alert('Token regenerated');
            // Re-view to update modal
            viewFamily(familyId);
            // Reload list in background
            loadFamilies();
        } else {
            alert('Failed to regenerate');
        }
    } catch (err) {
        console.error('Error:', err);
        alert('Server error');
    }
};

