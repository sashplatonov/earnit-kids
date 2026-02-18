import { loadDataFromServer, logout, loadBaseData, regenerateChildToken } from './api.js';
import { state, setState } from './state.js';
import { renderAll, renderTasks, renderShop } from './ui.js';
import { showToast, closeModal, openModal, handleConfirm } from './utils.js';
import { scheduleSave, buyItem, earnCoins, requestCoins, deleteHistoryItem, approveRequest, rejectRequest, deleteRequest, adminAwardCoins } from './actions.js';
import { openTaskModal, saveTask, deleteTask, editTask, openShopModal, saveShopItem, deleteShopItem, editShopItem, openChangePinModal, saveNewPin, openFamilySettingsModal, saveFamilySettings, saveFamilySettingsInline, saveNewPinInline, copyChildLinkInline, refreshChildLinkInline, regenerateChildLinkInline, switchChild, openAddChildModal, saveNewChild } from './admin.js';
import { renderRules, openEditRules, saveRules } from './rules.js';
import { handleSearch, addNewFriend, refreshFriends, saveNickname } from './friends.js';

// Catalog Logic
function renderCatalog() {
    const minInput = document.getElementById('catalog-age-min-filter');
    const maxInput = document.getElementById('catalog-age-max-filter');
    const minValSpan = document.getElementById('age-min-val');
    const maxValSpan = document.getElementById('age-max-val');
    const rangeHighlight = document.getElementById('slider-range-highlight');

    if (!minInput || !maxInput) return;

    let minAge = parseInt(minInput.value);
    let maxAge = parseInt(maxInput.value);

    // Keep min < max
    if (minAge > maxAge) {
        // Find which one was just moved
        // For simplicity, we just swap or cap
        const lastMoved = document.activeElement;
        if (lastMoved === minInput) {
            minAge = maxAge;
            minInput.value = minAge;
        } else {
            maxAge = minAge;
            maxInput.value = maxAge;
        }
    }

    if (minValSpan) minValSpan.textContent = minAge;
    if (maxValSpan) maxValSpan.textContent = maxAge;

    // Update range highlight
    if (rangeHighlight) {
        const minPercent = ((minAge - 7) / (18 - 7)) * 100;
        const maxPercent = ((maxAge - 7) / (18 - 7)) * 100;
        rangeHighlight.style.left = minPercent + '%';
        rangeHighlight.style.width = (maxPercent - minPercent) + '%';
    }

    const tasksList = document.getElementById('catalog-tasks-list');
    const productsList = document.getElementById('catalog-products-list');

    // Filtering logic: show item if its age range overlaps with selected range
    // Item is [t.age_min, t.age_max], Selected is [minAge, maxAge]
    // Overlap if (t.age_min <= maxAge && t.age_max >= minAge)

    if (tasksList && state.baseData.tasks) {
        const tasks = state.baseData.tasks.filter(t => t.age_min <= maxAge && t.age_max >= minAge);

        const grouped = tasks.reduce((acc, t) => {
            const cat = t.group || t.category || 'Без категории';
            if (!acc[cat]) acc[cat] = [];
            acc[cat].push(t);
            return acc;
        }, {});

        let html = '';
        Object.keys(grouped).sort().forEach(cat => {
            html += `<div class="category-header">${cat}</div>`;
            html += grouped[cat].sort((a, b) => a.coins - b.coins).map(t => {
                const freqText = t.frequency ? ` | ${t.frequency.limit}/${t.frequency.period}` : '';
                return `
                <div class="catalog-item">
                    <div class="catalog-info">
                        <span class="catalog-name">${t.name}</span>
                        <span class="catalog-meta">${t.coins} 🪙 | ${t.age_min}-${t.age_max} л.${freqText}</span>
                    </div>
                    <button class="btn-add" onclick="window.app.addCatalogItem('task', '${t.id}')">+</button>
                </div>
            `;
            }).join('');
        });
        tasksList.innerHTML = html;
    }

    if (productsList && state.baseData.products) {
        const products = state.baseData.products.filter(p => p.age_min <= maxAge && p.age_max >= minAge);

        const grouped = products.reduce((acc, p) => {
            const cat = p.group || p.category || 'Без категории';
            if (!acc[cat]) acc[cat] = [];
            acc[cat].push(p);
            return acc;
        }, {});

        let html = '';
        Object.keys(grouped).sort().forEach(cat => {
            html += `<div class="category-header">${cat}</div>`;
            html += grouped[cat].sort((a, b) => a.price - b.price).map(p => {
                const freqText = p.frequency ? ` | ${p.frequency.limit}/${p.frequency.period}` : '';
                const limitText = p.money_limit ? ` | Lim: ${p.money_limit}🪙` : '';
                return `
                    <div class="catalog-item">
                        <div class="catalog-info">
                            <span class="catalog-name">${p.name}</span>
                            <span class="catalog-meta">${p.price} 🪙 | ${p.age_min}-${p.age_max} л.${freqText}${limitText}</span>
                        </div>
                        <button class="btn-add" onclick="window.app.addCatalogItem('product', '${p.id}')">+</button>
                    </div>
                `;
            }).join('');
        });
        productsList.innerHTML = html;
    }
}

// About Logic
async function loadAboutContent() {
    const container = document.getElementById('about-content');
    if (!container) return;

    try {
        const res = await fetch('/about.html');
        if (res.ok) {
            const html = await res.text();
            const parser = new DOMParser();
            const doc = parser.parseFromString(html, 'text/html');
            const styleNode = doc.getElementById('about-landing-style');
            const contentNode = doc.getElementById('about-landing-content');

            if (contentNode) {
                const styleHtml = styleNode ? styleNode.outerHTML : '';
                container.innerHTML = `${styleHtml}${contentNode.outerHTML}`;
            } else {
                container.innerHTML = '<p>Ошибка: Не найден контент страницы</p>';
            }
        } else {
            container.innerHTML = '<p>Ошибка: Файл не найден</p>';
        }
    } catch (err) {
        console.error('Error loading about content:', err);
        container.innerHTML = '<p>Ошибка загрузки содержания</p>';
    }
}

function addCatalogItem(type, id) {
    const source = type === 'task' ? state.baseData.tasks : state.baseData.products;
    const item = source.find(i => i.id === id);

    if (!item) return;

    // Check for duplicates by name (Scoped to current child)
    const existing = type === 'task'
        ? state.tasks.find(t => t.name === item.name && t.childId == state.currentChildId)
        : state.shopItems.find(i => i.name === item.name && i.childId == state.currentChildId);

    if (existing) {
        showToast('Такой ' + (type === 'task' ? 'задание' : 'товар') + ' уже есть у этого ребенка!', 'error');
        return;
    }

    const newItem = {
        ...item,
        id: Date.now(), // New unique ID
        childId: state.currentChildId, // Assign to current child
        group: item.group || item.category || '', // Use group or category
        frequency: item.frequency || { limit: 1, period: 'day' }, // Use item freq or default
        money_limit: item.money_limit || null
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
    addCatalogItem,
    openFamilySettingsModal,
    saveFamilySettings,
    saveFamilySettingsInline,
    saveNewPinInline,
    copyChildLinkInline,
    regenerateChildLinkInline,
    switchChild,
    openAddChildModal,
    addNewFriend,
    handleSearch,
    saveNickname,
    adminAwardCoins
};

// Helper to get cookie value
function getCookie(name) {
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${name}=`);
    if (parts.length === 2) return parts.pop().split(';').shift();
}

function revealTopNav() {
    const nav = document.querySelector('.nav');
    if (nav) nav.classList.remove('nav--pending');
}

function bindClick(id, handler) {
    const element = document.getElementById(id);
    if (element) element.addEventListener('click', handler);
}

function bindInput(id, handler) {
    const element = document.getElementById(id);
    if (element) element.addEventListener('input', handler);
}

function showAdminNavActions() {
    bindClick('edit-rules-btn', openEditRules);

    const editRulesBtn = document.getElementById('edit-rules-btn');
    if (editRulesBtn) {
        editRulesBtn.classList.remove('hidden');
        editRulesBtn.parentElement.classList.remove('hidden');
    }

    const catBtn = document.getElementById('nav-catalog');
    if (catBtn) catBtn.classList.remove('hidden');

    const childLinkBtn = document.getElementById('nav-child-link');
    if (childLinkBtn) childLinkBtn.classList.remove('hidden');
}

function setupSettingsControls() {
    bindClick('settings-change-pin-btn', openChangePinModal);
    bindClick('settings-save-main-btn', saveFamilySettingsInline);
    bindClick('settings-save-pin-btn', saveNewPinInline);
    bindClick('settings-copy-link-btn', copyChildLinkInline);
    bindClick('settings-regenerate-link-btn', regenerateChildLinkInline);
    bindClick('settings-save-nickname-btn', saveNickname);

    if (!state.isAdmin) return;

    const nameInp = document.getElementById('settings-family-name-inline');
    if (nameInp) nameInp.value = state.familyName || '';

    const limitInp = document.getElementById('settings-money-limit-inline');
    if (limitInp) limitInp.value = state.monthlyLimit || 10000;

    refreshChildLinkInline();
}

async function fetchLegacyChildLink() {
    try {
        const res = await fetch('/api/child-link');
        const data = await res.json();
        if (!data.link) {
            showToast(`Ошибка получения ссылки: ${data.error || 'неизвестно'}`, 'error');
            return;
        }

        const input = document.getElementById('child-link-input');
        if (input) input.value = data.link;
        openModal('child-link-modal');
    } catch (err) {
        showToast('Ошибка сети', 'error');
    }
}

function setupChildLinkControls() {
    bindClick('settings-child-link-btn', fetchLegacyChildLink);
    bindClick('child-link-close', () => closeModal('child-link-modal'));

    bindClick('copy-child-link-btn', () => {
        const input = document.getElementById('child-link-input');
        if (!input) return;

        input.select();
        try {
            document.execCommand('copy');
            showToast('Ссылка скопирована!', 'success');
        } catch (err) {
            showToast('Не удалось скопировать', 'error');
        }
    });

    bindClick('regenerate-child-link-btn', async () => {
        if (!confirm('Вы уверены, что хотите обновить ссылку? Старая ссылка перестанет работать.')) return;
        const data = await regenerateChildToken();
        if (!data || !data.link) {
            showToast('Ошибка при обновлении ссылки', 'error');
            return;
        }

        const input = document.getElementById('child-link-input');
        if (input) input.value = data.link;
        showToast('Ссылка обновлена', 'success');
    });
}

function setupTaskAndShopControls() {
    bindClick('add-task-btn', () => openTaskModal());
    bindClick('task-save', saveTask);
    bindClick('task-cancel', () => closeModal('task-modal'));
    bindClick('task-delete', deleteTask);

    bindClick('add-shop-btn', () => openShopModal());
    bindClick('shop-save', saveShopItem);
    bindClick('shop-cancel', () => closeModal('shop-modal'));
    bindClick('shop-delete', deleteShopItem);
}

function setupRulesControls() {
    bindClick('rules-save', saveRules);
    bindClick('rules-cancel', () => closeModal('rules-modal'));
}

function setupCatalogFilters() {
    bindInput('catalog-age-min-filter', renderCatalog);
    bindInput('catalog-age-max-filter', renderCatalog);
}

function setupGeneralControls() {
    bindClick('logout-btn', async () => {
        if (await logout()) window.location.reload();
        else showToast('Ошибка при выходе', 'error');
    });

    bindClick('confirm-ok', handleConfirm);
    bindClick('confirm-cancel', () => closeModal('confirm-modal'));
    bindClick('add-child-save', saveNewChild);
    bindClick('add-child-cancel', () => closeModal('add-child-modal'));
    bindClick('friend-search-btn', handleSearch);

    const searchInput = document.getElementById('friend-search-input');
    if (searchInput) {
        searchInput.addEventListener('keypress', (event) => {
            if (event.key === 'Enter') handleSearch();
        });
    }

    bindClick('clear-history-btn', () => {
        if (!confirm('Очистить ВСЮ историю? Это нельзя отменить.')) return;
        setState({ history: [] });
        scheduleSave();
        renderAll();
        showToast('История очищена', 'info');
    });
}

function setupTabControls() {
    const tabButtons = document.querySelectorAll('.nav__btn, .nav__dropdown-item');
    const moreBtn = document.getElementById('nav-more-btn');
    const moreDropdown = document.getElementById('nav-more-dropdown');
    const closeChildMenus = () => {
        document.querySelectorAll('.child-menu.active').forEach((el) => el.classList.remove('active'));
    };

    const closeMoreDropdown = () => {
        if (!moreDropdown || moreDropdown.classList.contains('hidden')) return;
        moreDropdown.classList.add('hidden');
        if (moreBtn) moreBtn.setAttribute('aria-expanded', 'false');
        closeChildMenus();
    };

    const openMoreDropdown = () => {
        if (!moreDropdown || !moreDropdown.classList.contains('hidden')) return;
        closeChildMenus();
        moreDropdown.classList.remove('hidden');
        if (moreBtn) moreBtn.setAttribute('aria-expanded', 'true');
    };

    const activateTab = (tabName) => {
        if (!tabName) return;
        tabButtons.forEach((btn) => {
            btn.classList.toggle('active', btn.dataset.tab === tabName);
        });
        document.querySelectorAll('.section').forEach((section) => section.classList.add('hidden'));
        const targetSection = document.getElementById(`${tabName}-section`);
        if (targetSection) targetSection.classList.remove('hidden');
        closeChildMenus();
        closeMoreDropdown();
    };

    tabButtons.forEach((btn) => {
        btn.addEventListener('click', () => {
            activateTab(btn.dataset.tab);
            if (btn.classList.contains('nav__dropdown-item')) closeMoreDropdown();
        });
    });

    if (moreBtn && moreDropdown) {
        moreBtn.addEventListener('click', (event) => {
            event.stopPropagation();
            if (moreDropdown.classList.contains('hidden')) openMoreDropdown();
            else closeMoreDropdown();
        });

        moreDropdown.addEventListener('click', (event) => event.stopPropagation());
        document.addEventListener('click', (event) => {
            if (!event.target.closest('.nav__more-wrapper')) closeMoreDropdown();
        });
    }

    document.addEventListener('child-menu-visibility', (event) => {
        if (event.detail?.isActive) closeMoreDropdown();
    });

    const balanceButton = document.querySelector('.header__balance');
    if (balanceButton) {
        balanceButton.addEventListener('click', () => {
            activateTab('history');
            closeMoreDropdown();
        });
        balanceButton.addEventListener('keypress', (event) => {
            if (event.key !== 'Enter' && event.key !== ' ') return;
            event.preventDefault();
            activateTab('history');
            closeMoreDropdown();
        });
    }

    activateTab('tasks');
}

function setupModalBackdropClose() {
    document.querySelectorAll('.modal__backdrop').forEach((backdrop) => {
        backdrop.addEventListener('click', () => {
            const modal = backdrop.closest('.modal');
            if (modal) modal.classList.remove('active');
        });
    });
}

async function initializeFromServer() {
    const data = await loadDataFromServer();
    if (!data) {
        showToast('Не удалось загрузить данные с сервера', 'error');
        return;
    }

    let baseData = { tasks: [], products: [] };
    if (data.isAdmin) {
        baseData = await loadBaseData() || baseData;
    }

    setState(buildInitialState(data, baseData));

    if (!data.isAdmin) {
        await refreshFriends();
        return;
    }

    if (state.children && state.children.length > 0 && !state.currentChildId) {
        switchChild(state.children[0].id);
    }
}

function buildInitialState(data, baseData) {
    return {
        isAdmin: Boolean(data.isAdmin),
        role: data.isAdmin ? 'admin' : 'child',
        familyId: data.familyId ?? null,
        balance: data.balance ?? 0,
        tasks: data.tasks ?? [],
        shopItems: data.shop ?? [],
        history: data.history ?? [],
        requests: data.requests ?? [],
        familyName: data.familyName ?? '',
        childNickname: data.childNickname ?? null,
        monthlyLimit: data.monthlyLimit ?? 10000,
        dailyCoinLimit: data.dailyCoinLimit ?? 0,
        children: data.children ?? [],
        baseData
    };
}

async function initializeApp() {
    const role = getCookie('app_role') || 'child';
    await initializeFromServer();

    renderAll();
    renderRules();
    loadAboutContent();
    if (state.isAdmin) renderCatalog();

    if (state.isAdmin) showAdminNavActions();
    const setBtn = document.getElementById('nav-settings');
    if (setBtn) setBtn.classList.remove('hidden');

    setupRulesControls();
    setupCatalogFilters();
    setupSettingsControls();
    setupChildLinkControls();
    setupTaskAndShopControls();
    setupGeneralControls();
    setupTabControls();
    setupModalBackdropClose();
    revealTopNav();

    return role;
}

document.addEventListener('DOMContentLoaded', () => {
    initializeApp().catch((err) => {
        console.error('App init failed:', err);
        showToast('Ошибка инициализации приложения', 'error');
    });
});
