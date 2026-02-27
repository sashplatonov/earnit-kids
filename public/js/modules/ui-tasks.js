/** @file Ui Tasks frontend UI module */
import { escapeHtml, chunkedRender, isMobileViewport } from './utils.js';
import { CONFIG } from './ui-config.js';
import { applyStaggerReveal } from './motion-feedback.js';

const CARD_SHORTCUTS_KEY = '__earnitCardShortcuts';

function hasShortcut(set, id) {
    const numericId = Number(id);
    return set?.has(numericId) || set?.has(String(id));
}

function isShortcutActive(type, id) {
    if (typeof window === 'undefined') return false;
    const shortcuts = window[CARD_SHORTCUTS_KEY];
    return hasShortcut(shortcuts?.[type], id);
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

function renderTaskBadges(task) {
    const badges = [];
    if (task.group) {
        badges.push(renderBadge(task.group, 'group'));
    }
    if (!badges.length) return '';
    return `<div class="card__badge-row">${badges.join('')}</div>`;
}

function getAgeLabel(task) {
    const min = task.age_min ?? task.ageMin ?? task.minAge;
    const max = task.age_max ?? task.ageMax ?? task.maxAge;
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

function renderTaskMeta(task) {
    const meta = [];
    const ageLabel = getAgeLabel(task);
    if (ageLabel) meta.push(ageLabel);

    if (task.frequency?.period) {
        const limit = task.frequency.limit ?? 1;
        const periodLabel = formatPeriodLabel(task.frequency.period);
        if (periodLabel) {
            meta.push(`Повтор ${limit}/${periodLabel}`);
        }
    }

    return renderMetaRow(meta);
}

function getTaskActions(task, isAdmin) {
    if (isAdmin) {
        return `
            <button type="button" class="btn btn--success btn--small" onclick="window.app.earnCoins(${task.id})">✓ Начислить</button>
            <button type="button" class="btn btn--secondary btn--small" onclick="window.app.editTask(${task.id})">Изменить</button>
        `;
    }
    return `<button type="button" class="btn btn--primary btn--small" onclick="window.app.requestCoins(${task.id})">✋ Выполнено</button>`;
}

function renderTaskCard(task, isAdmin) {
    const badgeRow = renderTaskBadges(task);
    const metaRow = renderTaskMeta(task);
    const isBookmarked = isShortcutActive('task', task.id);
    const highlightClass = isBookmarked ? ' card--highlight' : '';
    const quickActions = `
        <div class="card__quick-actions">
            <button type="button" class="btn btn--secondary btn--small card__quick-bookmark${isBookmarked ? ' card__quick-bookmark--active' : ''}" aria-pressed="${isBookmarked ? 'true' : 'false'}" onclick="window.app.toggleCardBookmark('task', ${task.id}, this)">${isBookmarked ? 'В быстрых' : 'В быстрые'}</button>
        </div>
    `;
    return `
        <div class="card card--task${highlightClass}" data-id="${task.id}">
            ${badgeRow}
            <div class="card__header">
                <h3 class="card__title">${escapeHtml(task.name)}</h3>
                <div class="card__coins"><span>${task.coins}</span><span class="gamified-icon icon-coin-stack" aria-hidden="true"></span></div>
            </div>
            ${task.comment ? `<p class="card__comment">${escapeHtml(task.comment)}</p>` : ''}
            ${metaRow}
            <div class="card__actions">${getTaskActions(task, isAdmin)}</div>
            ${quickActions}
        </div>
    `;
}

function splitTasksByQuick(tasks) {
    const quickTasks = tasks.filter(task => isShortcutActive('task', task.id));
    const quickIds = new Set(quickTasks.map(task => String(task.id)));
    const regularTasks = tasks.filter(task => !quickIds.has(String(task.id)));
    return { quickTasks, regularTasks };
}

function buildTaskRenderQueue({ grouped, sortedGroups, quickTasks, isAdmin }) {
    const renderQueue = [];
    if (quickTasks.length) {
        renderQueue.push('<div class="group-header">Быстрые</div>');
        quickTasks.sort((a, b) => a.coins - b.coins)
            .forEach(task => renderQueue.push(renderTaskCard(task, isAdmin)));
    }
    sortedGroups.forEach(groupName => {
        renderQueue.push(`<div class="group-header">${escapeHtml(groupName)}</div>`);
        grouped[groupName].sort((a, b) => a.coins - b.coins)
            .forEach(task => renderQueue.push(renderTaskCard(task, isAdmin)));
    });
    return renderQueue;
}

export function renderTasksUI(state) {
    const container = document.getElementById('tasks-list');
    const emptyState = document.getElementById('tasks-empty');
    if (!container) return;

    if (state.tasks.length === 0) {
        container.innerHTML = '';
        if (emptyState) emptyState.classList.remove('hidden');
        return;
    }
    if (emptyState) emptyState.classList.add('hidden');

    let tasks = state.tasks.filter(t => !t.isDeleted);
    if (state.isAdmin && state.currentChildId) {
        tasks = tasks.filter(t => !t.childId || t.childId == state.currentChildId);
    }

    const { quickTasks, regularTasks } = splitTasksByQuick(tasks);

    const grouped = regularTasks.reduce((acc, t) => {
        const g = t.group || 'Без категории';
        if (!acc[g]) acc[g] = [];
        acc[g].push(t);
        return acc;
    }, {});

    const sortedGroups = Object.keys(grouped).sort((a, b) => {
        if (a === 'Без категории') return 1;
        if (b === 'Без категории') return -1;
        return a.localeCompare(b);
    });

    const renderQueue = buildTaskRenderQueue({ grouped, sortedGroups, quickTasks, isAdmin: state.isAdmin });

    chunkedRender(container, renderQueue, { chunkSize: isMobileViewport() ? 5 : 10 });
    window.setTimeout(() => applyStaggerReveal(container), 40);
}
