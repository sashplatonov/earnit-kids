/** @file UI Today & Progress sections */
import { escapeHtml } from './utils.js';
import { state } from './state.js';
import { CONFIG } from './ui-config.js';

const WEEKLY_EARN_GOAL = CONFIG.WEEKLY_GOAL || 220;
const WEEKLY_SPEND_GOAL = CONFIG.WEEKLY_SPEND_GOAL || 150;
const XP_PER_LEVEL = CONFIG.XP_PER_LEVEL || 120;
const MAX_TODAY_TASKS = 3;
const REQUEST_STATUS_LABELS = {
    pending: 'В обработке',
    approved: 'Подтверждена',
    rejected: 'Отклонена',
    expired: 'Истекла'
};
const REQUEST_STATUS_ORDER = ['pending', 'approved', 'rejected', 'expired'];
function resolveActiveChildId(state) {
    if (state.isAdmin) {
        return state.currentChildId || state.children[0]?.id || null;
    }
    return state.children[0]?.id || null;
}

function resolveChildName(state, childId) {
    if (!childId) return '';
    const child = state.children.find(c => c.id == childId);
    return child ? child.name : '';
}

function filterTasksForChild(tasks, childId) {
    if (!Array.isArray(tasks)) return [];
    const activeTasks = tasks.filter(task => !task.isDeleted);
    if (!childId) return activeTasks;
    return activeTasks.filter(task => !task.childId || task.childId == childId);
}

function filterHistoryByChild(history, childId) {
    if (!Array.isArray(history)) return [];
    if (!childId) return history;
    return history.filter(entry => entry.childId == childId);
}

function formatCoins(value) {
    return `${value.toLocaleString('ru-RU')} <span class="gamified-icon icon-coin-stack" aria-hidden="true" style="width: 1.2rem; height: 1.2rem; vertical-align: middle;"></span>`;
}

function getActiveBalance(state) {
    if (state.isAdmin && state.currentChildId) {
        const child = state.children.find(c => c.id == state.currentChildId);
        if (child) return child.balance;
    }
    return state.balance;
}

function getPriorityTasks(state, childId) {
    return filterTasksForChild(state.tasks, childId)
        .sort((a, b) => (b.coins || 0) - (a.coins || 0))
        .slice(0, MAX_TODAY_TASKS);
}

function renderTodayTask(task) {
    const periodLabel = task.frequency && task.frequency.period
        ? CONFIG.PERIODS[task.frequency.period]?.display || task.frequency.period
        : null;
    const periodMarkup = periodLabel ? `<span class="tag tag--activity">${task.frequency.limit}/${periodLabel}</span>` : '';
    return `
        <div class="today-task">
            <div class="today-task__title">${escapeHtml(task.name)}</div>
            <div class="today-task__meta">
                <span>${task.coins || 0} <span class="gamified-icon icon-coin-stack" aria-hidden="true" style="width: 1rem; height: 1rem; vertical-align: middle;"></span></span>
                ${periodMarkup}
            </div>
        </div>
    `;
}

function getRequestCounts(state, childId) {
    const counts = { pending: 0, approved: 0, rejected: 0, expired: 0 };
    (state.requests || []).forEach((req) => {
        if (childId && req.childId != childId) return;
        const status = req.status || 'pending';
        if (!counts[status]) counts[status] = 0;
        counts[status] += 1;
    });
    return counts;
}

function renderRequestSummary(state, childId) {
    const counts = getRequestCounts(state, childId);
    const total = Object.values(counts).reduce((sum, value) => sum + value, 0);
    if (total === 0) {
        return '<p>Нет активных заявок</p>';
    }
    return REQUEST_STATUS_ORDER.map((status) => {
        const count = counts[status];
        if (!count) return '';
        const label = REQUEST_STATUS_LABELS[status] || status;
        return `
            <div class="today-request-row">
                <span>${label}</span>
                <span class="tag tag--status tag--status-${status}">${count}</span>
            </div>
        `;
    }).join('');
}

function formatChildLabel(count) {
    if (count === 0) return 'детей';
    if (count === 1) return 'ребенок';
    if (count > 1 && count < 5) return 'ребенка';
    return 'детей';
}

function formatRequestLabel(count) {
    if (count === 0) return 'заявок';
    if (count === 1) return 'заявка';
    if (count > 1 && count < 5) return 'заявки';
    return 'заявок';
}

function renderParentOverview(state) {
    const children = Array.isArray(state.children) ? state.children : [];
    const totalChildren = children.length;
    const childNames = children.map(child => child.nickname || child.name || '—').filter(Boolean);
    const pendingRequests = getRequestCounts(state).pending || 0;

    updateElementText('parent-overview-children', `${totalChildren} ${formatChildLabel(totalChildren)}`);
    const childNote = childNames.length
        ? `Активные профили: ${childNames.join(', ')}`
        : 'Пока нет активных профилей.';
    updateElementText('parent-overview-children-note', childNote);

    updateElementText('parent-overview-requests-count', `${pendingRequests} ${formatRequestLabel(pendingRequests)}`);
    const requestNote = pendingRequests
        ? 'Ожидают решения от вас'
        : 'Новых заявок нет';
    updateElementText('parent-overview-requests-note', requestNote);

    const parentSummary = document.getElementById('parent-request-summary');
    if (parentSummary) {
        parentSummary.innerHTML = renderRequestSummary(state);
    }
}

function updateElementText(id, text) {
    const el = document.getElementById(id);
    if (el) el.textContent = text;
}

function updateBar(id, percent) {
    const fill = document.getElementById(id);
    if (fill) {
        fill.style.width = `${Math.min(Math.max(percent, 0), 100)}%`;
    }
}

function parseNumberFromText(text) {
    if (!text) return 0;
    const numeric = text.replace(/[^0-9.-]/g, '');
    const parsed = parseFloat(numeric);
    return Number.isFinite(parsed) ? parsed : 0;
}

function animateNumberText(id, target, options = {}) {
    const config = typeof options === 'function' ? { formatter: options } : options;
    const formatter = config.formatter ?? ((value) => Math.round(value).toString());
    const duration = config.duration ?? 600;
    const el = document.getElementById(id);
    if (!el) return;
    const startValue = parseFloat(el.dataset.lastValue ?? parseNumberFromText(el.textContent)) || 0;
    const startTime = performance.now();
    const easeOut = (t) => 1 - Math.pow(1 - t, 3);

    const animate = (timestamp) => {
        const progress = Math.min((timestamp - startTime) / duration, 1);
        const current = startValue + (target - startValue) * easeOut(progress);
        el.textContent = formatter(current);
        if (progress < 1) {
            requestAnimationFrame(animate);
        } else {
            el.dataset.lastValue = target;
        }
    };

    requestAnimationFrame(animate);
}

function renderEmptyChildrenToday(section) {
    section.innerHTML = `
        <div class="card empty-state-card" style="text-align: center; padding: 40px 20px;">
            <div class="empty-state-card__icon" style="font-size: 3rem; margin-bottom: 1rem;">👶</div>
            <h3>Нет детей в профиле</h3>
            <p style="color: rgba(255,255,255,0.6); margin-bottom: 1.5rem;">Чтобы начать пользоваться системой, добавьте своего первого ребенка.</p>
            <button class="btn btn--success" onclick="window.app.openAddChildModal()">+ Добавить ребенка</button>
        </div>
    `;
}

export function renderTodayUI(state) {
    const section = document.getElementById('today-section');
    if (!section) return;

    if (state.isAdmin && (!state.children || state.children.length === 0)) {
        renderEmptyChildrenToday(section);
        renderParentOverview(state);
        return;
    }

    const childId = resolveActiveChildId(state);
    const childName = resolveChildName(state, childId);
    const primaryName = childName || state.children[0]?.name || 'Aliska';
    updateTodayStats(state, childId, primaryName);
    renderTodayLists(state, childId);
    renderParentOverview(state);
}

function updateTodayStats(state, childId, primaryName) {
    const balance = getActiveBalance(state);
    const balanceEl = document.getElementById('today-balance-value');
    if (balanceEl) balanceEl.innerHTML = formatCoins(balance);
    updateElementText('today-balance-note', 'Следите за балансом и достижениями.');
    updateElementText('today-child-label', `Данные для ${primaryName}`);
}

function renderTodayLists(state, childId) {
    const tasksList = document.getElementById('today-task-list');
    if (tasksList) {
        const tasks = getPriorityTasks(state, childId);
        tasksList.innerHTML = tasks.length
            ? tasks.map(renderTodayTask).join('')
            : '<p>Добавьте задания, чтобы увидеть, что сделать сегодня.</p>';
    }

    const summary = document.getElementById('today-request-summary');
    if (summary) {
        summary.innerHTML = renderRequestSummary(state, childId);
    }
}

