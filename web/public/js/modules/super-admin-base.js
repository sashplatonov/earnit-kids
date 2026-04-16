/** @file Super Admin Base frontend UI module */
/**
 * Super Admin Base Data Management Module
 */
import { showSuperAlert, showSuperConfirm } from './super-admin-dialogs.js';
import { fetchWithCsrf } from './api.js';

let baseData = { tasks: [], products: [] };

export function setBaseData(data) { baseData = data; }
export function getBaseData() { return baseData; }

export function renderList(type, items, container) {
    container.innerHTML = '';
    const grouped = items.reduce((acc, item) => {
        const cat = item.group || item.category || 'Без категории';
        if (!acc[cat]) acc[cat] = [];
        acc[cat].push(item);
        return acc;
    }, {});

    Object.keys(grouped).sort().forEach(cat => {
        const groupSection = document.createElement('section');
        groupSection.className = 'catalog-group';

        const h = document.createElement('h3');
        h.className = 'grid-category-header';
        h.textContent = cat;
        groupSection.appendChild(h);

        const groupGrid = document.createElement('div');
        groupGrid.className = 'catalog-group__grid';
        grouped[cat].forEach(item => {
            const idx = baseData[type].findIndex(i => i.id === item.id);
            const card = document.createElement('div');
            card.className = 'item-card';
            const f = item.frequency ? ` (${item.frequency.limit}/${item.frequency.period})` : '';
            const mL = item.money_limit ? ` | Limit: ${item.money_limit} 🪙` : '';
            card.innerHTML = `
                <div class="item-header"><span>${item.name}</span></div>
                <div class="item-meta" style="color: #6366f1; font-weight: 600;">${item.coins || item.price} 🪙 ${f}${mL}</div>
                <div class="item-meta">Возраст: ${item.age_min}-${item.age_max} лет</div>
                <div class="item-actions">
                    <button class="btn-sm btn-edit" onclick="editItem('${type}', ${idx})">✏️</button>
                    <button class="btn-sm btn-del" onclick="deleteItem('${type}', ${idx})">🗑️</button>
                </div>
            `;
            groupGrid.appendChild(card);
        });
        groupSection.appendChild(groupGrid);
        container.appendChild(groupSection);
    });
}

function cloneBaseData() {
    return JSON.parse(JSON.stringify(baseData));
}

async function saveToServer(nextBaseData) {
    try {
        const response = await fetchWithCsrf('/api/super/base-data', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(nextBaseData)
        });
        if (!response.ok) {
            throw new Error(`save failed: ${response.status}`);
        }
        baseData = nextBaseData;
        return true;
    } catch (err) {
        await showSuperAlert({ title: 'Ошибка сохранения', message: 'Не удалось сохранить изменения каталога.' });
        return false;
    }
}

export async function deleteItem(type, index) {
    const confirmed = await showSuperConfirm({
        title: 'Удалить карточку?',
        message: 'Элемент будет удален из базового каталога.',
        confirmText: 'Удалить'
    });
    if (!confirmed) return false;
    const nextBaseData = cloneBaseData();
    nextBaseData[type].splice(index, 1);
    return saveToServer(nextBaseData);
}

export async function saveItem(type, index, newItem) {
    const nextBaseData = cloneBaseData();
    if (index === -1) {
        nextBaseData[type].push(newItem);
    } else {
        nextBaseData[type][index] = { ...nextBaseData[type][index], ...newItem };
    }
    return saveToServer(nextBaseData);
}
