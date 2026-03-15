/** @file Ui Shop frontend UI module */
import { escapeHtml, chunkedRender, isMobileViewport } from './utils.js';
import { CONFIG } from './ui-config.js';
import { applyStaggerReveal } from './motion-feedback.js';
import { renderGroupNav } from './group-nav.js';

const CARD_SHORTCUTS_KEY = '__earnitCardShortcuts';

function hasShortcut(set, id) {
    const numericId = Number(id);
    return set?.has(numericId) || set?.has(String(id));
}

function getShopShortcutSet() {
    if (typeof window === 'undefined') return new Set();
    const shortcuts = window[CARD_SHORTCUTS_KEY];
    if (!shortcuts?.shop) return new Set();
    if (shortcuts.shop instanceof Set) return shortcuts.shop;
    return shortcuts.shop.quick || new Set();
}

function isShortcutActive(type, id) {
    if (typeof window === 'undefined') return false;
    if (type !== 'shop') {
        const shortcuts = window[CARD_SHORTCUTS_KEY];
        return hasShortcut(shortcuts?.[type], id);
    }
    const shortcuts = getShopShortcutSet();
    return hasShortcut(shortcuts, id);
}

function formatPeriodLabel(period) {
    if (!period) return '';
    const info = CONFIG.PERIODS[period];
    return info?.display || period;
}

function renderBadge(label, variant = '') {
    if (!label) return '';
    const classes = ['card__badge'];
    if (variant) classes.push(`card__badge--${variant}`);
    return `<span class="${classes.join(' ')}">${escapeHtml(label)}</span>`;
}

function renderShopBadges(item, canAfford) {
    const badges = [];

    // Add status as the first badge
    badges.push(renderShopStatus(canAfford));

    if (item.type) {
        const typeLabel = CONFIG.SHOP_ITEM_TYPES[item.type]?.label || item.type;
        badges.push(renderBadge(typeLabel, 'type'));
    }

    if (item.money_limit) {
        badges.push(renderBadge(`Не более ${item.money_limit} 💶`, 'money'));
    }

    return `<div class="card__badge-row">${badges.join('')}</div>`;
}

function getAgeLabel(item) {
    const min = item.age_min ?? item.ageMin ?? item.minAge;
    const max = item.age_max ?? item.ageMax ?? item.maxAge;
    if (min && max) {
        return `Возраст ${min}–${max}`;
    }
    if (min) {
        return `Возраст от ${min}`;
    }
    if (max) {
        return `Возраст до ${max}`;
    }
    return '';
}

function renderMetaRow(parts) {
    if (!parts.length) return '';
    const escaped = parts.map(part => `<span class="card__meta-item">${escapeHtml(part)}</span>`);
    return `<div class="card__meta">${escaped.join('<span class="card__meta-sep" aria-hidden="true">•</span>')}</div>`;
}

function renderShopMeta(item) {
    const meta = [];
    const ageLabel = getAgeLabel(item);
    if (ageLabel) meta.push(ageLabel);

    if (item.frequency?.period) {
        const limit = item.frequency.limit ?? 1;
        const periodLabel = formatPeriodLabel(item.frequency.period);
        if (periodLabel) {
            meta.push(`Повтор ${limit}/${periodLabel}`);
        }
    }

    return renderMetaRow(meta);
}

function renderShopStatus(canAfford) {
    const label = canAfford ? 'Готово к покупке' : 'Требуются монеты';
    const variant = canAfford ? 'available' : 'locked';
    return `<span class="card__status card__status--${variant}">${label}</span>`;
}

function getShopActions(item, canAfford, state) {
    const disabledAttrs = canAfford ? '' : 'disabled aria-disabled="true"';
    return `
        <button type="button" class="btn btn--primary btn--small" onclick="window.app.buyItem(${item.id})" ${disabledAttrs}>${canAfford ? 'Купить' : 'Не хватает'}</button>
        ${state.isAdmin ? `<button type="button" class="btn btn--secondary btn--small" onclick="window.app.editShopItem(${item.id})">Изменить</button>` : ''}
    `;
}

function splitShopItemsByPins(items) {
    const quickItems = items.filter(item => isShortcutActive('shop', item.id));
    const pinnedIds = new Set(quickItems.map(item => String(item.id)));
    const regularItems = items.filter(item => !pinnedIds.has(String(item.id)));
    return { quickItems, regularItems };
}

function renderPinnedShopSections({ renderQueue, quickItems, state, activeGroup }) {
    if (quickItems.length && (activeGroup === 'Все' || activeGroup === '⭐ Избранное')) {
        renderQueue.push('<div class="group-header">⭐ Избранное</div>');
        quickItems.sort((a, b) => a.price - b.price)
            .forEach(item => renderQueue.push(renderShopItemCard(item, state)));
    }
}

function renderGroupedShopSections({ renderQueue, grouped, groupNames, state, activeGroup }) {
    groupNames.forEach(groupName => {
        if (activeGroup === 'Все' || activeGroup === groupName) {
            renderQueue.push(`<div class="group-header">${escapeHtml(groupName)}</div>`);
            grouped[groupName].sort((a, b) => a.price - b.price)
                .forEach(item => renderQueue.push(renderShopItemCard(item, state)));
        }
    });
}

function renderShopItemCard(item, state) {
    const canAfford = state.balance >= item.price;
    const badges = renderShopBadges(item, canAfford);
    const meta = renderShopMeta(item);
    const isQuick = isShortcutActive('shop', item.id);
    const highlightClass = isQuick ? ' card--highlight' : '';
    const affordableClass = canAfford ? ' card--affordable' : '';
    
    const bookmarkBtn = `<button type="button" class="card__bookmark-btn${isQuick ? ' card__bookmark-btn--active' : ''}" aria-pressed="${isQuick ? 'true' : 'false'}" title="${isQuick ? 'Убрать из избранного' : 'В избранное'}" onclick="window.app.toggleCardBookmark('shop', ${item.id}, this)">${isQuick ? '★' : '☆'}</button>`;
    
    return `
        <div class="card card--shop${highlightClass}${affordableClass}" data-id="${item.id}">
            ${badges}
            <div class="card__header">
                <h3 class="card__title">${escapeHtml(item.name)}</h3>
                <div class="card__coins"><span>${item.price}</span><span class="gamified-icon icon-coin-stack" aria-hidden="true"></span></div>
            </div>
            ${item.comment ? `<p class="card__comment">${escapeHtml(item.comment)}</p>` : ''}
            <div class="card__footer-row">
                ${metaRow(item)}
                ${bookmarkBtn}
            </div>
            <div class="card__actions">
                ${getShopActions(item, canAfford, state)}
            </div>
        </div>
    `;
}

/** Helper to render shop meta inside the card - using existing renderShopMeta logic but checking if actually needed */
function metaRow(item) {
    return renderShopMeta(item);
}

let currentActiveGroup = 'Все';

function renderShopGroupNav(groupNames, quickItems) {
    const allGroupNames = [];
    if (quickItems.length) allGroupNames.push('⭐ Избранное');
    allGroupNames.push(...groupNames);
    
    // Fallback if active group was deleted
    if (currentActiveGroup !== 'Все' && !allGroupNames.includes(currentActiveGroup)) {
        currentActiveGroup = 'Все';
    }

    renderGroupNav('shop-group-nav', {
        groups: allGroupNames, 
        activeGroup: currentActiveGroup, 
        onChange: (newGroup) => {
            currentActiveGroup = newGroup;
            import('./ui.js').then(module => module.renderShop());
        }
    });
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

    const { quickItems, regularItems } = splitShopItemsByPins(items);

    const grouped = regularItems.reduce((acc, item) => {
        const g = item.group || 'Без категории';
        if (!acc[g]) acc[g] = [];
        acc[g].push(item);
        return acc;
    }, {});

    const groupNames = Object.keys(grouped).sort((a, b) => {
        if (a === 'Без категории') return 1;
        if (b === 'Без категории') return -1;
        return a.localeCompare(b);
    });

    const renderQueue = [];
    renderPinnedShopSections({ renderQueue, quickItems, state, activeGroup: currentActiveGroup });
    renderGroupedShopSections({ renderQueue, grouped, groupNames, state, activeGroup: currentActiveGroup });

    chunkedRender(container, renderQueue, { chunkSize: isMobileViewport() ? 5 : 10 });

    renderShopGroupNav(groupNames, quickItems);

    window.setTimeout(() => applyStaggerReveal(container), 40);
}
