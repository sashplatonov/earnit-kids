import { escapeHtml } from './utils.js';
import { CONFIG } from './ui-config.js';

function getTaskTags(task) {
    if (!task.frequency || !task.frequency.period) return '';
    const periodInfo = CONFIG.PERIODS[task.frequency.period];
    const display = periodInfo ? periodInfo.display : task.frequency.period;
    return `<div style="margin-bottom:0.5rem;"><span class="tag">${task.frequency.limit}/${display}</span></div>`;
}

function getTaskActions(task, isAdmin) {
    if (isAdmin) {
        return `
            <button class="btn btn--success btn--small" onclick="window.app.earnCoins(${task.id})">✓ Начислить</button>
            <button class="btn btn--secondary btn--small" onclick="window.app.editTask(${task.id})">✏️ Изменить</button>
        `;
    }
    return `<button class="btn btn--primary btn--small" onclick="window.app.requestCoins(${task.id})">✋ Выполнено</button>`;
}

function renderTaskCard(task, isAdmin) {
    return `
        <div class="card" data-id="${task.id}">
            <div class="card__header">
                <h3 class="card__title">${escapeHtml(task.name)}</h3>
                <div class="card__coins"><span>${task.coins}</span><span>🪙</span></div>
            </div>
            ${getTaskTags(task)}
            ${task.comment ? `<p class="card__comment">${escapeHtml(task.comment)}</p>` : ''}
            <div class="card__actions">${getTaskActions(task, isAdmin)}</div>
        </div>
    `;
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

    const grouped = tasks.reduce((acc, t) => {
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

    container.innerHTML = sortedGroups.map(groupName => {
        const items = grouped[groupName].sort((a, b) => a.coins - b.coins)
            .map(task => renderTaskCard(task, state.isAdmin)).join('');
        return `<div class="group-header">${escapeHtml(groupName)}</div>${items}`;
    }).join('');
}
