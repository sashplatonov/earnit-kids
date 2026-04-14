/** @file Super-admin families list rendering and actions */
import { renderFamilyDetails } from './super-admin-family-details.js';
import { applyFamiliesFilters, getFamilyChildrenCount } from './super-admin-filters.js';
import { showSuperAlert, showSuperConfirm } from './super-admin-dialogs.js';
import { fetchWithCsrf } from './api.js';

let familiesData = [];
const familiesById = new Map();
const familiesViewState = {
    status: 'all',
    sort: 'created',
    search: ''
};
const familiesList = document.getElementById('families-list');
const familiesLoadingState = document.getElementById('loading');
const familiesErrorState = document.getElementById('families-error');

function escapeHtml(value) {
    const div = document.createElement('div');
    div.textContent = String(value ?? '');
    return div.innerHTML;
}

function formatDate(value, withTime = false) {
    if (!value) return '-';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '-';
    return withTime ? date.toLocaleString('ru-RU') : date.toLocaleDateString('ru-RU');
}

function refreshFamilyLookup(rows) {
    familiesById.clear();
    rows.forEach((family) => {
        familiesById.set(String(family.id), family);
    });
}

async function loadFamilies() {
    if (familiesLoadingState) {
        familiesLoadingState.textContent = 'Загрузка...';
        familiesLoadingState.hidden = false;
    }
    if (familiesErrorState) familiesErrorState.hidden = true;
    if (familiesTable) familiesTable.hidden = true;

    try {
        const res = await fetch('/api/super/families');
        if (!res.ok) {
            throw new Error('failed to load');
        }
        const data = await res.json();
        familiesData = data.families || [];
        refreshFamilyLookup(familiesData);
        renderFamilies();
    } catch (err) {
        console.error('Error:', err);
        if (familiesLoadingState) familiesLoadingState.hidden = true;
        if (familiesErrorState) {
            familiesErrorState.textContent = 'Не удалось загрузить семьи. Попробуйте позже.';
            familiesErrorState.hidden = false;
        }
    }
}

function findLatestFamily(families) {
    let latest = null;
    for (const family of families) {
        if (!latest || new Date(family.created_at) > new Date(latest.created_at)) {
            latest = family;
        }
    }
    return latest;
}

function updateStats(families) {
    const totalEl = document.getElementById('total-families');
    const latestEl = document.getElementById('latest-family');
    if (totalEl) totalEl.textContent = String(families.length);
    if (!latestEl) return;
    const latest = findLatestFamily(families);
    latestEl.textContent = latest ? `${latest.email || latest.id} (${formatDate(latest.created_at, true)})` : '-';
}

function getFamilyStatusBadge(isBlocked) {
    return isBlocked
        ? '<span class="family-status family-status--blocked">Заблок.</span>'
        : '<span class="family-status family-status--active">Активна</span>';
}

function getFamilyBlockButtonLabel(isBlocked) {
    return isBlocked ? '🔓' : '🔒';
}

function getFamilyBlockButtonClass(isBlocked) {
    return isBlocked ? 'unblock' : '';
}

function buildFamilyCardHtml(family) {
    const id = escapeHtml(family.id);
    const email = escapeHtml(family.email || '-');
    const childrenCount = getFamilyChildrenCount(family);
    const lastActivity = family.last_activity ? formatDate(family.last_activity, true) : 'нет данных';
    return `
        <div class="family-avatar">👨‍👩‍👧</div>
        <div class="family-info">
            <div class="family-name">${email}</div>
            <div class="family-meta">${childrenCount} детей · последний вход ${lastActivity} · ${family.tasksCount || 0} заданий · ${family.shopCount || 0} товаров</div>
        </div>
        <div class="family-actions">
            ${getFamilyStatusBadge(family.isBlocked)}
            <button class="view-btn" type="button" data-action="view-family" data-family-id="${id}" title="Детали">Детали</button>
            <button class="block-btn ${getFamilyBlockButtonClass(family.isBlocked)}" type="button" data-action="toggle-family-block" data-family-id="${id}">
                ${getFamilyBlockButtonLabel(family.isBlocked)}
            </button>
        </div>
    `;
}

function showFamilyState(message) {
    if (!familiesLoadingState) return;
    familiesLoadingState.textContent = message;
    familiesLoadingState.hidden = false;
    if (familiesList) familiesList.hidden = true;
}

function hideFamilyState() {
    if (!familiesLoadingState) return;
    familiesLoadingState.hidden = true;
    if (familiesList) familiesList.hidden = false;
}

function appendFamilyRows(rows) {
    if (!familiesList) return;
    rows.forEach((family) => {
        const card = document.createElement('div');
        card.className = 'family-card';
        card.innerHTML = buildFamilyCardHtml(family);
        familiesList.appendChild(card);
    });
}

function renderFamilies() {
    if (!familiesLoadingState) return;
    if (familiesList) familiesList.innerHTML = '';
    if (familiesErrorState) familiesErrorState.hidden = true;

    if (familiesData.length === 0) {
        showFamilyState('Семьи пока не добавлены');
        updateStats([]);
        return;
    }

    const visibleFamilies = applyFamiliesFilters([...familiesData], familiesViewState);
    if (!visibleFamilies.length) {
        showFamilyState('По текущим фильтрам семьи не найдены');
        updateStats([]);
        return;
    }

    hideFamilyState();
    updateStats(visibleFamilies);
    appendFamilyRows(visibleFamilies);
}

function setupFamiliesControls() {
    const statusChips = document.querySelectorAll('[data-filter-status]');
    const sortChips = document.querySelectorAll('[data-sort]');
    const searchInput = document.getElementById('families-search');

    statusChips.forEach((chip) => {
        chip.addEventListener('click', () => {
            familiesViewState.status = chip.dataset.filterStatus || 'all';
            statusChips.forEach((node) => node.classList.toggle('active', node === chip));
            renderFamilies();
        });
    });

    sortChips.forEach((chip) => {
        chip.addEventListener('click', () => {
            familiesViewState.sort = chip.dataset.sort || 'created';
            sortChips.forEach((node) => node.classList.toggle('active', node === chip));
            renderFamilies();
        });
    });

    if (searchInput) {
        searchInput.addEventListener('input', () => {
            familiesViewState.search = searchInput.value.trim();
            renderFamilies();
        });
    }

    const statusSelect = document.getElementById('families-status-select');
    if (statusSelect) {
        statusSelect.addEventListener('change', () => {
            familiesViewState.status = statusSelect.value;
            renderFamilies();
        });
    }
}

async function toggleBlock(familyId, shouldBlock) {
    const family = familiesById.get(String(familyId));
    const familyLabel = family?.email || familyId;
    const confirmed = await showSuperConfirm({
        title: shouldBlock ? 'Заблокировать семью?' : 'Разблокировать семью?',
        message: `${familyLabel}: ${shouldBlock ? 'доступ к кабинету будет закрыт.' : 'доступ к кабинету будет восстановлен.'}`,
        confirmText: shouldBlock ? 'Заблокировать' : 'Разблокировать'
    });
    if (!confirmed) return;

    try {
        const res = await fetchWithCsrf(`/api/super/family/${encodeURIComponent(familyId)}/block`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ isBlocked: shouldBlock })
        });
        if (res.ok) {
            await loadFamilies();
            return;
        }
        await showSuperAlert({ title: 'Ошибка', message: 'Не удалось изменить статус семьи.' });
    } catch (err) {
        await showSuperAlert({ title: 'Ошибка сети', message: 'Проверьте подключение и повторите действие.' });
    }
}

async function viewFamily(familyId) {
    const modal = document.getElementById('family-modal');
    const modalTitle = document.getElementById('modal-title');
    const modalBody = document.getElementById('modal-body');
    if (!modal || !modalTitle || !modalBody) return;
    modal.classList.add('active');
    modalTitle.textContent = 'Загрузка...';
    modalBody.innerHTML = '<div class="loading">Загрузка данных...</div>';
    try {
        const res = await fetch(`/api/super/family/${encodeURIComponent(familyId)}/data`);
        if (res.ok) {
            renderFamilyDetails(await res.json());
            return;
        }
        modalBody.innerHTML = '<p style="color: #ef4444;">Ошибка загрузки</p>';
    } catch (err) {
        modalBody.innerHTML = '<p style="color: #ef4444;">Ошибка связи</p>';
    }
}

function copyMagicLink(token) {
    if (!token) {
        showSuperAlert({ title: 'Ссылка недоступна', message: 'У ребенка не найден токен входа.' });
        return;
    }
    navigator.clipboard.writeText(`${window.location.origin}/login-child/${token}`)
        .then(() => showSuperAlert({ title: 'Готово', message: 'Ссылка скопирована в буфер обмена.' }))
        .catch(() => showSuperAlert({ title: 'Ошибка', message: 'Не удалось скопировать ссылку.' }));
}

async function regenerateToken(familyId, childId) {
    const confirmed = await showSuperConfirm({
        title: 'Обновить magic-link?',
        message: 'Старая ссылка перестанет работать.',
        confirmText: 'Обновить'
    });
    if (!confirmed) return;

    const url = childId
        ? `/api/super/child/${encodeURIComponent(childId)}/regenerate-token`
        : `/api/super/family/${encodeURIComponent(familyId)}/regenerate-token`;
    try {
        const res = await fetchWithCsrf(url, { method: 'POST' });
        if (res.ok) {
            await showSuperAlert({ title: 'Готово', message: 'Новый токен создан.' });
            await loadFamilies();
            return;
        }
        await showSuperAlert({ title: 'Ошибка', message: 'Не удалось обновить токен.' });
    } catch (err) {
        await showSuperAlert({ title: 'Ошибка сети', message: 'Проверьте подключение и повторите действие.' });
    }
}

function bindFamiliesActions() {
    if (!familiesList || familiesList.dataset.bound === 'true') return;
    familiesList.dataset.bound = 'true';
    familiesList.addEventListener('click', (event) => {
        const actionButton = event.target.closest('button[data-action]');
        if (!actionButton) return;
        const familyId = actionButton.dataset.familyId;
        if (!familyId) return;
        const action = actionButton.dataset.action;
        if (action === 'view-family') {
            viewFamily(familyId);
            return;
        }
        if (action === 'toggle-family-block') {
            const family = familiesById.get(String(familyId));
            toggleBlock(familyId, !Boolean(family?.isBlocked));
        }
    });
}

document.getElementById('modal-close')?.addEventListener('click', () => {
    document.getElementById('family-modal')?.classList.remove('active');
});
document.getElementById('family-modal')?.addEventListener('click', (event) => {
    if (event.target.id === 'family-modal') {
        document.getElementById('family-modal')?.classList.remove('active');
    }
});

export function initFamiliesPanel() {
    setupFamiliesControls();
    bindFamiliesActions();
    window.copyMagicLink = copyMagicLink;
    window.regenerateToken = regenerateToken;
    loadFamilies();
}
