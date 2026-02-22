/**
 * Super Admin Base Data Management Module
 */

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
        const h = document.createElement('h3');
        h.className = 'grid-category-header';
        h.textContent = cat;
        container.appendChild(h);

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
            container.appendChild(card);
        });
    });
}

async function saveToServer() {
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

export async function deleteItem(type, index) {
    if (confirm('Удалить?')) {
        baseData[type].splice(index, 1);
        await saveToServer();
        return true;
    }
    return false;
}

export async function saveItem(type, index, newItem) {
    if (index === -1) {
        baseData[type].push(newItem);
    } else {
        baseData[type][index] = { ...baseData[type][index], ...newItem };
    }
    await saveToServer();
    return true;
}
