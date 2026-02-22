import { state } from './state.js';
import { escapeHtml } from './utils.js';
import { updateBudgetStatsUI } from './budget-ui.js';
import { renderChildSwitcherUI } from './child-switcher-ui.js';
import { CONFIG } from './ui-config.js';
import { renderTasksUI } from './ui-tasks.js';
import { renderShopUI } from './ui-shop.js';
import { renderRequestsUI } from './ui-requests.js';
import { renderHistoryUI } from './ui-history.js';
import { renderFriendsUI } from './ui-friends.js';

export const renderTasks = () => renderTasksUI(state);
export const renderShop = () => renderShopUI(state);
export const renderRequests = () => renderRequestsUI(state);
export const renderHistory = () => renderHistoryUI(state);
export const renderFriends = () => renderFriendsUI(state);

function getActiveChildId() {
    if (state.isAdmin) return state.currentChildId || null;
    if (state.children && state.children.length > 0) return state.children[0].id;
    return null;
}

export function updateBalanceUI() {
    let displayBalance = state.balance;
    if (state.isAdmin && state.currentChildId) {
        const child = state.children.find(c => c.id == state.currentChildId);
        if (child) displayBalance = child.balance;
    }
    const balanceEl = document.getElementById('balance');
    if (balanceEl) balanceEl.textContent = displayBalance;
    updateBudgetStatsUI(state, CONFIG, getActiveChildId());
}

export function renderAll() {
    updateBalanceUI();
    renderTasksUI(state);
    renderRequestsUI(state);
    renderShopUI(state);
    renderHistoryUI(state);
    renderFriendsUI(state);
    updateAdminUI();
    updateShopNameUI();
    renderChildSwitcherUI(state, escapeHtml);
}

export function updateAdminUI() {
    const isParent = !!state.isAdmin;
    document.querySelectorAll('.admin-only, .parent-only').forEach(el => el.classList.toggle('hidden', !isParent));
    document.querySelectorAll('.child-only').forEach(el => el.classList.toggle('hidden', isParent));

    // Keep settings button visible for everyone
    const settingsBtn = document.getElementById('settings-btn') || document.getElementById('nav-settings');
    if (settingsBtn) settingsBtn.classList.remove('hidden');
}

function updateElementText(id, text) {
    const el = document.getElementById(id);
    if (el) el.textContent = text;
}

function updateElementValue(id, value) {
    const el = document.getElementById(id);
    if (el) el.value = value;
}

export function updateShopNameUI() {
    updateElementText('shop-name-display', state.familyName || '');
    updateElementText('child-nickname-display', state.childNickname ? `(${state.childNickname})` : '');
    updateElementValue('settings-family-name-inline', state.familyName || '');
    updateElementValue('settings-nickname', state.childNickname || '');
}
