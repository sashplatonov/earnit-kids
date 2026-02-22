import { escapeHtml } from './utils.js';
import { CONFIG } from './ui-config.js';

function getTypeTag(type) {
    if (!type) return '';
    const label = CONFIG.SHOP_ITEM_TYPES[type]?.label || type;
    return `<span class="tag tag--${type}">${label}</span>`;
}

function getLimitTag(item) {
    const mLimit = item.moneyLimit || item.money_limit;
    return mLimit ? `<span class="tag tag--money">Lim: ${mLimit} 🪙</span>` : '';
}

function getFrequencyTag(item) {
    if (!item.frequency?.period) return '';
    const periodInfo = CONFIG.PERIODS[item.frequency.period];
    const display = periodInfo?.display || item.frequency.period;
    return `<span class="tag">${item.frequency.limit}/${display}</span>`;
}

function getShopItemTags(item) {
    const tags = [getTypeTag(item.type), getLimitTag(item), getFrequencyTag(item)].filter(Boolean);
    return tags.length ? `<div style="margin-bottom:0.5rem;">${tags.join('')}</div>` : '';
}

function renderShopItemCard(item, state) {
    const canAfford = state.balance >= item.price;
    return `
        <div class="card ${canAfford ? 'card--affordable' : ''}" data-id="${item.id}">
            <div class="card__header">
                <h3 class="card__title">${escapeHtml(item.name)}</h3>
                <div class="card__coins"><span>${item.price}</span><span>🪙</span></div>
            </div>
            ${getShopItemTags(item)}
            ${item.comment ? `<p class="card__comment">${escapeHtml(item.comment)}</p>` : ''}
            <div class="card__actions">
                <button class="btn btn--primary btn--small" onclick="window.app.buyItem(${item.id})" ${!canAfford ? 'disabled style="opacity:0.5;cursor:not-allowed;"' : ''}>
                    🛒 ${canAfford ? 'Купить' : 'Не хватает'}
                </button>
                ${state.isAdmin ? `<button class="btn btn--secondary btn--small" onclick="window.app.editShopItem(${item.id})">✏️ Изменить</button>` : ''}
            </div>
        </div>
    `;
}

export function renderShopUI(state) {
    const container = document.getElementById('shop-list');
    const emptyState = document.getElementById('shop-empty');
    if (!container) return;

    let items = state.shopItems.filter(i => !i.isDeleted);
    if (state.isAdmin && state.currentChildId) {
        items = items.filter(i => !i.childId || i.childId == state.currentChildId);
    }

    if (items.length === 0) {
        container.innerHTML = '';
        if (emptyState) emptyState.classList.remove('hidden');
        return;
    }
    if (emptyState) emptyState.classList.add('hidden');

    const grouped = items.reduce((acc, item) => {
        const g = item.group || 'Без категории';
        if (!acc[g]) acc[g] = [];
        acc[g].push(item);
        return acc;
    }, {});

    container.innerHTML = Object.keys(grouped).sort((a, b) => {
        if (a === 'Без категории') return 1;
        if (b === 'Без категории') return -1;
        return a.localeCompare(b);
    }).map(groupName => {
        const html = grouped[groupName].sort((a, b) => a.price - b.price).map(item => renderShopItemCard(item, state)).join('');
        return `<div class="group-header">${escapeHtml(groupName)}</div>${html}`;
    }).join('');
}
