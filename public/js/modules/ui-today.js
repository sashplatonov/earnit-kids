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
    return `${value.toLocaleString('ru-RU')} мон.`;
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
                <span>${task.coins || 0} мон.</span>
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

function getWeeklyStats(history) {
    const now = new Date();
    const startOfWeek = new Date(now);
    startOfWeek.setHours(0, 0, 0, 0);
    startOfWeek.setDate(startOfWeek.getDate() - 6);

    const rangeStart = startOfWeek.getTime();
    const rangeEnd = now.getTime();

    return (history || []).reduce((acc, entry) => {
        const entryDate = entry.date ? new Date(entry.date).getTime() : 0;
        if (!entryDate || entryDate < rangeStart || entryDate > rangeEnd) return acc;
        if (entry.type === 'earn') acc.earned += entry.amount || 0;
        if (entry.type === 'spend') acc.spent += entry.amount || 0;
        return acc;
    }, { earned: 0, spent: 0 });
}

function computeStreak(history) {
    let streak = 0;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    let pointer = new Date(today);

    const hasEarnOn = (dateStr) => (history || []).some((entry) => {
        return entry.type === 'earn' && entry.date && entry.date.startsWith(dateStr);
    });

    while (true) {
        const key = pointer.toISOString().slice(0, 10);
        if (!hasEarnOn(key)) break;
        streak += 1;
        pointer.setDate(pointer.getDate() - 1);
    }
    return streak;
}

function computeLevel(history) {
    const totalXp = (history || []).reduce((sum, entry) => entry.type === 'earn' ? sum + (entry.amount || 0) : sum, 0);
    const level = Math.max(1, Math.floor(totalXp / XP_PER_LEVEL) + 1);
    const xpInLevel = totalXp % XP_PER_LEVEL;
    const xpToNext = XP_PER_LEVEL - xpInLevel;
    const progress = xpInLevel / XP_PER_LEVEL * 100;
    return { level, xpToNext, progress: Number.isFinite(progress) ? progress : 0 };
}

export function renderTodayUI(state) {
    const section = document.getElementById('today-section');
    if (!section) return;

    const childId = resolveActiveChildId(state);
    const childName = resolveChildName(state, childId);
    const primaryName = childName || state.children[0]?.name || 'Aliska';
    const balance = getActiveBalance(state);
    updateElementText('today-balance-value', formatCoins(balance));
    updateElementText('today-balance-note', 'Следите за балансом и достижениями.');
    updateElementText('today-child-label', `Данные для ${primaryName}`);

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

    renderParentOverview(state);
}

export function renderProgressUI(state) {
    const section = document.getElementById('progress-section');
    if (!section) return;

    const childId = resolveActiveChildId(state);
    const childName = resolveChildName(state, childId);
    updateElementText('progress-child-name', childName ? `Ребенок: ${childName}` : 'Статистика для всех детей');
    const filteredHistory = filterHistoryByChild(state.history, childId);

    const weekStats = getWeeklyStats(filteredHistory);
    animateNumberText('progress-week-earned-value', weekStats.earned, value => formatCoins(Math.round(value)));
    animateNumberText('progress-week-spent-value', weekStats.spent, value => formatCoins(Math.round(value)));
    updateElementText('progress-week-earned-goal', `Цель: ${WEEKLY_EARN_GOAL} мон.`);
    updateElementText('progress-week-spent-note', `Не больше ${WEEKLY_SPEND_GOAL} мон.`);
    updateBar('progress-week-earned-bar', (weekStats.earned / WEEKLY_EARN_GOAL) * 100);
    updateBar('progress-week-spent-bar', (weekStats.spent / WEEKLY_SPEND_GOAL) * 100);

    const streak = computeStreak(filteredHistory);
    animateNumberText('progress-streak-value', streak, value => Math.round(value).toString());
    updateElementText('progress-streak-note', streak > 0 ? 'дней подряд с задачей' : 'Начните сегодня!');

    const levelInfo = computeLevel(filteredHistory);
    animateNumberText('progress-level-value', levelInfo.level, value => `Lv ${Math.max(1, Math.floor(value))}`);
    updateElementText('progress-level-note', `до следующего уровня ${levelInfo.xpToNext} XP`);
    updateBar('progress-level-bar', levelInfo.progress);

}
