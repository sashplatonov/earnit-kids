/** @file Ui frontend UI module */
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
import { renderTodayUI, renderProgressUI } from './ui-today.js';

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
    if (balanceEl) {
        const currentVal = parseInt(balanceEl.textContent || '0', 10);
        if (currentVal !== displayBalance) {
            animateValue(balanceEl, { start: currentVal, end: displayBalance }, 260);
            showBalanceDelta(displayBalance - currentVal);
        } else {
            balanceEl.textContent = displayBalance;
        }
    }
    updateBudgetStatsUI(state, CONFIG, getActiveChildId());
}

function showBalanceDelta(delta) {
    if (!Number.isFinite(delta) || delta === 0) return;
    const deltaEl = document.getElementById('header-balance-delta');
    if (!deltaEl) return;

    deltaEl.classList.remove('hidden', 'is-visible', 'is-negative');
    deltaEl.textContent = `${delta > 0 ? '+' : ''}${delta}`;
    if (delta < 0) deltaEl.classList.add('is-negative');

    // Reflow to restart the same animation every update.
    void deltaEl.offsetWidth;
    deltaEl.classList.add('is-visible');

    window.setTimeout(() => {
        deltaEl.classList.remove('is-visible');
        deltaEl.classList.add('hidden');
    }, 800);
}

function animateValue(obj, range, duration) {
    let startTimestamp = null;
    const step = (timestamp) => {
        if (!startTimestamp) startTimestamp = timestamp;
        const progress = Math.min((timestamp - startTimestamp) / duration, 1);
        const easeOut = 1 - Math.pow(1 - progress, 4);
        obj.textContent = Math.floor(easeOut * (range.end - range.start) + range.start);
        if (progress < 1) {
            window.requestAnimationFrame(step);
        } else {
            obj.textContent = range.end;
        }
    };
    window.requestAnimationFrame(step);
}

export function renderAll() {
    updateBalanceUI();
    renderTasksUI(state);
    renderRequestsUI(state);
    renderShopUI(state);
    renderHistoryUI(state);
    renderFriendsUI(state);
    renderTodayUI(state);
    renderProgressUI(state);
    updateAdminUI();
    updateChildNicknameUI();
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

export function updateChildNicknameUI() {
    updateElementText('child-nickname-display', state.childNickname ? `(${state.childNickname})` : '');
    updateElementValue('settings-nickname', state.childNickname || '');
}
