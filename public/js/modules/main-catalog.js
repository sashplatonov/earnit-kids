/** @file Main Catalog frontend UI module */
import { state } from './state.js';
import { renderTasks, renderShop } from './ui.js';
import { showToast } from './utils.js';
import { scheduleSave } from './actions.js';
import { renderGroupNav } from './group-nav.js';

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
            const lim = t.money_limit ? ` | Lim: ${t.money_limit} мон.` : '';
            return `
                <div class="catalog-item">
                    <div class="catalog-info">
                        <span class="catalog-name">${t.name}</span>
                        <span class="catalog-meta">${val} мон. | ${t.age_min}-${t.age_max} л.${freq}${lim}</span>
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

let activeTasksGroup = 'Все';
let activeProductsGroup = 'Все';

function renderCatalogGroupNavs(tasks, products) {
    const getGroups = (items) => [...new Set(items.map(t => t.group || t.category || 'Без категории'))].sort();
    
    const tasksGroups = getGroups(tasks);
    if (activeTasksGroup !== 'Все' && !tasksGroups.includes(activeTasksGroup)) {
        activeTasksGroup = 'Все';
    }

    renderGroupNav('catalog-tasks-group-nav', {
        groups: tasksGroups, 
        activeGroup: activeTasksGroup, 
        onChange: (newGroup) => {
            activeTasksGroup = newGroup;
            renderCatalog();
        }
    });
    
    const productsGroups = getGroups(products);
    if (activeProductsGroup !== 'Все' && !productsGroups.includes(activeProductsGroup)) {
        activeProductsGroup = 'Все';
    }

    renderGroupNav('catalog-products-group-nav', {
        groups: productsGroups, 
        activeGroup: activeProductsGroup, 
        onChange: (newGroup) => {
            activeProductsGroup = newGroup;
            renderCatalog();
        }
    });
}

function getAgeFilterRange(minInput, maxInput) {
    let minAge = parseInt(minInput.value);
    let maxAge = parseInt(maxInput.value);

    if (minAge > maxAge) {
        if (document.activeElement === minInput) minAge = maxAge; else maxAge = minAge;
        minInput.value = minAge; maxInput.value = maxAge;
    }
    return { minAge, maxAge };
}

function updateCatalogUI(minAge, maxAge) {
    const setVal = (id, val) => { const el = document.getElementById(id); if (el) el.textContent = val; };
    setVal('age-min-val', minAge); setVal('age-max-val', maxAge);
    updateSliderHighlight(minAge, maxAge);

    const filterAge = (arr) => (arr || []).filter(i => i.age_min <= maxAge && i.age_max >= minAge);
    const tasks = filterAge(state.baseData.tasks);
    const products = filterAge(state.baseData.products);
    
    const renderTasksItems = activeTasksGroup === 'Все' ? tasks : tasks.filter(t => (t.group || t.category || 'Без категории') === activeTasksGroup);
    const renderProductsItems = activeProductsGroup === 'Все' ? products : products.filter(t => (t.group || t.category || 'Без категории') === activeProductsGroup);
    
    const tList = document.getElementById('catalog-tasks-list');
    const pList = document.getElementById('catalog-products-list');
    
    if (tList) tList.innerHTML = getCatalogHtml(renderTasksItems, 'task');
    if (pList) pList.innerHTML = getCatalogHtml(renderProductsItems, 'product');
    
    renderCatalogGroupNavs(tasks, products);
}

export function renderCatalog() {
    const minInput = document.getElementById('catalog-age-min-filter');
    const maxInput = document.getElementById('catalog-age-max-filter');
    if (!minInput || !maxInput) return;

    const { minAge, maxAge } = getAgeFilterRange(minInput, maxInput);
    updateCatalogUI(minAge, maxAge);
}

function checkDuplicateInCatalog(type, name) {
    const list = type === 'task' ? state.tasks : state.shopItems;
    return list.some(i => !i.isDeleted && i.name === name && i.childId == state.currentChildId);
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
