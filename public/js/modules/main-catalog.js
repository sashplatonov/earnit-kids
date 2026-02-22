import { state } from './state.js';
import { renderTasks, renderShop } from './ui.js';
import { showToast } from './utils.js';
import { scheduleSave } from './actions.js';

function getCatalogHtml(items, type) {
    const grouped = items.reduce((acc, t) => {
        const cat = t.group || t.category || 'Без категории';
        if (!acc[cat]) acc[cat] = [];
        acc[cat].push(t);
        return acc;
    }, {});

    return Object.keys(grouped).sort().map(cat => {
        const header = `<div class="category-header">${cat}</div>`;
        const list = grouped[cat].sort((a, b) => (a.coins || a.price) - (b.coins || b.price)).map(t => {
            const freq = t.frequency ? ` | ${t.frequency.limit}/${t.frequency.period}` : '';
            const val = t.coins || t.price;
            const lim = t.money_limit ? ` | Lim: ${t.money_limit}🪙` : '';
            return `
                <div class="catalog-item">
                    <div class="catalog-info">
                        <span class="catalog-name">${t.name}</span>
                        <span class="catalog-meta">${val} 🪙 | ${t.age_min}-${t.age_max} л.${freq}${lim}</span>
                    </div>
                    <button class="btn-add" onclick="window.app.addCatalogItem('${type}', '${t.id}')">+</button>
                </div>
            `;
        }).join('');
        return header + list;
    }).join('');
}

function updateSliderHighlight(minAge, maxAge) {
    const hl = document.getElementById('slider-range-highlight');
    if (hl) {
        const minP = ((minAge - 7) / 11) * 100;
        const maxP = ((maxAge - 7) / 11) * 100;
        hl.style.left = minP + '%'; hl.style.width = (maxP - minP) + '%';
    }
}

export function renderCatalog() {
    const minInput = document.getElementById('catalog-age-min-filter');
    const maxInput = document.getElementById('catalog-age-max-filter');
    if (!minInput || !maxInput) return;

    let minAge = parseInt(minInput.value);
    let maxAge = parseInt(maxInput.value);

    if (minAge > maxAge) {
        if (document.activeElement === minInput) minAge = maxAge; else maxAge = minAge;
        minInput.value = minAge; maxInput.value = maxAge;
    }

    const setVal = (id, val) => { const el = document.getElementById(id); if (el) el.textContent = val; };
    setVal('age-min-val', minAge); setVal('age-max-val', maxAge);
    updateSliderHighlight(minAge, maxAge);

    const filterAge = (arr) => (arr || []).filter(i => i.age_min <= maxAge && i.age_max >= minAge);
    const tList = document.getElementById('catalog-tasks-list');
    const pList = document.getElementById('catalog-products-list');
    if (tList) tList.innerHTML = getCatalogHtml(filterAge(state.baseData.tasks), 'task');
    if (pList) pList.innerHTML = getCatalogHtml(filterAge(state.baseData.products), 'product');
}

function checkDuplicateInCatalog(type, name) {
    const list = type === 'task' ? state.tasks : state.shopItems;
    return list.some(i => i.name === name && i.childId == state.currentChildId);
}

function buildNewCatalogItem(item) {
    const newItem = {
        ...item, id: Date.now(), childId: state.currentChildId,
        group: item.group || item.category || '',
        frequency: item.frequency || { limit: 1, period: 'day' },
        money_limit: item.money_limit || null
    };
    delete newItem.age_min; delete newItem.age_max;
    return newItem;
}

export function addCatalogItem(type, id) {
    const isTask = type === 'task';
    const source = isTask ? state.baseData.tasks : state.baseData.products;
    const item = source.find(i => i.id === id);
    if (!item) return;

    if (checkDuplicateInCatalog(type, item.name)) {
        return showToast(`Такой ${isTask ? 'задание' : 'товар'} уже есть у этого ребенка!`, 'error');
    }

    const newItem = buildNewCatalogItem(item);
    if (isTask) { state.tasks.push(newItem); renderTasks(); }
    else { state.shopItems.push(newItem); renderShop(); }

    showToast(isTask ? 'Задание добавлено' : 'Товар добавлен', 'success');
    scheduleSave();
}
