import { loadDataFromServer, logout, loadBaseData, regenerateChildToken } from './api.js';
import { state, setState } from './state.js';
import { renderAll, renderTasks, renderShop } from './ui.js';
import { showToast, closeModal, openModal, handleConfirm } from './utils.js';
import { scheduleSave, buyItem, earnCoins, requestCoins, deleteHistoryItem, approveRequest, rejectRequest, deleteRequest } from './actions.js';
import { openTaskModal, saveTask, deleteTask, editTask, openShopModal, saveShopItem, deleteShopItem, editShopItem, openChangePinModal, saveNewPin, openFamilySettingsModal, saveFamilySettings, saveFamilySettingsInline, saveNewPinInline, copyChildLinkInline, refreshChildLinkInline, regenerateChildLinkInline } from './admin.js';
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
        const res = await fetch('/about.md');
        if (res.ok) {
            const text = await res.text();

            if (window.marked) {
                container.innerHTML = window.marked.parse(text);
            } else {
                container.innerHTML = `<pre>${text}</pre>`;
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

    // Check for duplicates by name
    const existing = type === 'task'
        ? state.tasks.find(t => t.name === item.name)
        : state.shopItems.find(i => i.name === item.name);

    if (existing) {
        showToast('Такой ' + (type === 'task' ? 'задание' : 'товар') + ' уже есть!', 'error');
        return;
    }

    const newItem = {
        ...item,
        id: Date.now(), // New unique ID
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
    addNewFriend,
    handleSearch,
    saveNickname
};

// Helper to get cookie value
function getCookie(name) {
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${name}=`);
    if (parts.length === 2) return parts.pop().split(';').shift();
}

// Initialization
document.addEventListener('DOMContentLoaded', async () => {
    // Determine role from cookie
    const role = getCookie('app_role') || 'child';

    // Load data
    const data = await loadDataFromServer();
    if (data) {
        // Load Base Data if Admin
        let baseData = { tasks: [], products: [] };
        if (data.isAdmin) {
            baseData = await loadBaseData() || baseData;
        }

        setState({
            isAdmin: data.isAdmin || false,
            role: data.isAdmin ? 'admin' : 'child',
            familyId: data.familyId || null,
            balance: data.balance || 0,
            tasks: data.tasks || [],
            shopItems: data.shop || [],
            history: data.history || [],
            requests: data.requests || [],
            familyName: data.familyName || '',
            childNickname: data.childNickname || null,
            monthlyLimit: data.monthlyLimit || 2000,
            baseData: baseData
        });

        if (!data.isAdmin) {
            await refreshFriends();
        }
    } else {
        showToast('Не удалось загрузить данные с сервера', 'error');
    }

    renderAll();
    renderRules();
    loadAboutContent();
    // Render catalog if admin
    if (state.isAdmin) renderCatalog();

    // Show rules edit button if admin
    if (state.isAdmin) {
        const editRulesBtn = document.getElementById('edit-rules-btn');
        if (editRulesBtn) {
            editRulesBtn.classList.remove('hidden');
            editRulesBtn.parentElement.classList.remove('hidden');
            editRulesBtn.addEventListener('click', openEditRules);
        }
    }

    const rulesSave = document.getElementById('rules-save');
    if (rulesSave) rulesSave.addEventListener('click', saveRules);

    const rulesCancel = document.getElementById('rules-cancel');
    if (rulesCancel) rulesCancel.addEventListener('click', () => closeModal('rules-modal'));

    // Show catalog and settings buttons if admin
    const setBtn = document.getElementById('nav-settings');
    if (setBtn) setBtn.classList.remove('hidden');

    if (state.isAdmin) {
        const catBtn = document.getElementById('nav-catalog');
        if (catBtn) catBtn.classList.remove('hidden');
        const childLinkBtn = document.getElementById('nav-child-link');
        if (childLinkBtn) childLinkBtn.classList.remove('hidden');
    }

    // Event Listeners

    // Catalog Filter
    const ageMinFilter = document.getElementById('catalog-age-min-filter');
    const ageMaxFilter = document.getElementById('catalog-age-max-filter');
    if (ageMinFilter) ageMinFilter.addEventListener('input', renderCatalog);
    if (ageMaxFilter) ageMaxFilter.addEventListener('input', renderCatalog);

    // Logout
    const logoutBtn = document.getElementById('logout-btn');
    if (logoutBtn) logoutBtn.addEventListener('click', async () => {
        if (await logout()) {
            window.location.reload();
        } else {
            showToast('Ошибка при выходе', 'error');
        }
    });

    // Change PIN
    const changePinBtn = document.getElementById('settings-change-pin-btn');
    if (changePinBtn) {
        changePinBtn.addEventListener('click', openChangePinModal);
    }

    // Inline Settings Save
    const saveMainBtn = document.getElementById('settings-save-main-btn');
    if (saveMainBtn) saveMainBtn.addEventListener('click', saveFamilySettingsInline);

    const savePinBtn = document.getElementById('settings-save-pin-btn');
    if (savePinBtn) savePinBtn.addEventListener('click', saveNewPinInline);

    const copyLinkBtnInline = document.getElementById('settings-copy-link-btn');
    if (copyLinkBtnInline) copyLinkBtnInline.addEventListener('click', copyChildLinkInline);

    const regenerateLinkBtnInline = document.getElementById('settings-regenerate-link-btn');
    if (regenerateLinkBtnInline) regenerateLinkBtnInline.addEventListener('click', regenerateChildLinkInline);

    // Initial populate settings if admin
    if (state.isAdmin) {
        const nameInp = document.getElementById('settings-family-name-inline');
        if (nameInp) nameInp.value = state.familyName || '';
        const limitInp = document.getElementById('settings-money-limit-inline');
        if (limitInp) limitInp.value = state.monthlyLimit || 10000;

        // Populate child link
        refreshChildLinkInline();
    }

    // Child Link Modal (Legacy or other uses, keeping for now or replacing)
    const childLinkBtn = document.getElementById('settings-child-link-btn');
    if (childLinkBtn) {
        childLinkBtn.addEventListener('click', async () => {
            try {
                const res = await fetch('/api/child-link');
                const data = await res.json();
                if (data.link) {
                    const input = document.getElementById('child-link-input');
                    if (input) input.value = data.link;
                    openModal('child-link-modal');
                } else {
                    showToast('Ошибка получения ссылки: ' + (data.error || 'неизвестно'), 'error');
                }
            } catch (err) {
                showToast('Ошибка сети', 'error');
            }
        });
    }

    const copyLinkBtn = document.getElementById('copy-child-link-btn');
    if (copyLinkBtn) {
        copyLinkBtn.addEventListener('click', () => {
            const input = document.getElementById('child-link-input');
            if (input) {
                input.select();
                try {
                    document.execCommand('copy');
                    showToast('Ссылка скопирована!', 'success');
                } catch (err) {
                    showToast('Не удалось скопировать', 'error');
                }
            }
        });
    }

    const childLinkClose = document.getElementById('child-link-close');
    if (childLinkClose) {
        childLinkClose.addEventListener('click', () => closeModal('child-link-modal'));
    }

    const regenerateChildLinkBtn = document.getElementById('regenerate-child-link-btn');
    if (regenerateChildLinkBtn) {
        regenerateChildLinkBtn.addEventListener('click', async () => {
            if (!confirm('Вы уверены, что хотите обновить ссылку? Старая ссылка перестанет работать.')) return;
            const data = await regenerateChildToken();
            if (data && data.link) {
                const input = document.getElementById('child-link-input');
                if (input) input.value = data.link;
                showToast('Ссылка обновлена', 'success');
            } else {
                showToast('Ошибка при обновлении ссылки', 'error');
            }
        });
    }

    // Tasks
    const addTaskBtn = document.getElementById('add-task-btn');
    if (addTaskBtn) addTaskBtn.addEventListener('click', () => openTaskModal());

    const taskSave = document.getElementById('task-save');
    if (taskSave) taskSave.addEventListener('click', saveTask);

    const taskCancel = document.getElementById('task-cancel');
    if (taskCancel) taskCancel.addEventListener('click', () => closeModal('task-modal'));

    const taskDelete = document.getElementById('task-delete');
    if (taskDelete) taskDelete.addEventListener('click', deleteTask);

    // Shop
    const addShopBtn = document.getElementById('add-shop-btn');
    if (addShopBtn) addShopBtn.addEventListener('click', () => openShopModal());

    const shopSave = document.getElementById('shop-save');
    if (shopSave) shopSave.addEventListener('click', saveShopItem);

    const shopCancel = document.getElementById('shop-cancel');
    if (shopCancel) shopCancel.addEventListener('click', () => closeModal('shop-modal'));

    const shopDelete = document.getElementById('shop-delete');
    if (shopDelete) shopDelete.addEventListener('click', deleteShopItem);

    // Confirmation
    const confirmOk = document.getElementById('confirm-ok');
    if (confirmOk) confirmOk.addEventListener('click', handleConfirm);

    const confirmCancel = document.getElementById('confirm-cancel');
    if (confirmCancel) confirmCancel.addEventListener('click', () => closeModal('confirm-modal'));

    // Tabs
    document.querySelectorAll('.nav__btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const tabName = btn.dataset.tab;
            document.querySelectorAll('.nav__btn').forEach(b => b.classList.toggle('active', b.dataset.tab === tabName));
            document.querySelectorAll('.section').forEach(s => s.classList.add('hidden'));
            const section = document.getElementById(`${tabName}-section`);
            if (section) section.classList.remove('hidden');
        });
    });

    // Modals backdrop
    document.querySelectorAll('.modal__backdrop').forEach(backdrop => {
        backdrop.addEventListener('click', () => {
            const modal = backdrop.closest('.modal');
            if (modal) modal.classList.remove('active');
        });
    });



    // History
    const clearHistoryBtn = document.getElementById('clear-history-btn');
    if (clearHistoryBtn) {
        clearHistoryBtn.addEventListener('click', () => {
            if (!confirm('Очистить ВСЮ историю? Это нельзя отменить.')) return;
            setState({ history: [] });
            scheduleSave();
            renderAll();
            showToast('История очищена', 'info');
        });
    }

    // Friends Section
    const searchBtn = document.getElementById('friend-search-btn');
    if (searchBtn) searchBtn.addEventListener('click', handleSearch);

    const searchInput = document.getElementById('friend-search-input');
    if (searchInput) searchInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') handleSearch();
    });

    const saveNicknameBtn = document.getElementById('settings-save-nickname-btn');
    if (saveNicknameBtn) saveNicknameBtn.addEventListener('click', saveNickname);
});
