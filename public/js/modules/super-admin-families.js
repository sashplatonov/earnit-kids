import { renderFamilyDetails } from './super-admin-family-details.js';
import { applyFamiliesFilters, getFamilyChildrenCount } from './super-admin-filters.js';

let familiesData = [];
const familiesViewState = {
    status: 'all',
    sort: 'created',
    search: ''
};
const familiesTable = document.getElementById('families-table');
const familiesLoadingState = document.getElementById('loading');
const familiesErrorState = document.getElementById('families-error');

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
    document.getElementById('total-families').textContent = families.length;
    const latest = findLatestFamily(families);
    document.getElementById('latest-family').textContent = latest ? `${latest.email || latest.id} (${new Date(latest.created_at).toLocaleString('ru-RU')})` : '-';
}

function getFamilyStatusBadge(isBlocked) {
    return isBlocked ? '<span style="color:#ef4444; font-weight:700;">ЗАБЛОКИРОВАНА</span>' : '<span style="color:#16a34a; font-weight:700;">АКТИВНА</span>';
}

function getFamilyBlockButtonLabel(isBlocked) {
    return isBlocked ? '🔓' : '🔒';
}

function getFamilyBlockButtonClass(isBlocked) {
    return isBlocked ? 'unblock' : '';
}

function buildFamilyRowHtml(f) {
    return `
            <td style="opacity:0.5" class="hide-mobile">#${f.id}</td>
            <td><strong>${f.email || f.id}</strong></td>
            <td class="hide-mobile">${f.email || '-'}</td>
            <td class="hide-mobile"><code>${f.admin_password || 'N/A'}</code></td>
            <td class="hide-mobile"><code>${f.admin_password || 'N/A'}</code></td>
            <td class="hide-mobile">${f.tasksCount || 0} <span style="opacity:0.7;">(👧 ${getFamilyChildrenCount(f)})</span></td>
            <td class="hide-mobile">${f.shopCount || 0}</td>
            <td class="hide-mobile">${f.monthly_limit || 10000} 🪙</td>
            <td>${getFamilyStatusBadge(f.isBlocked)}</td>
            <td class="hide-mobile">${new Date(f.created_at).toLocaleDateString('ru-RU')}</td>
            <td class="hide-mobile" style="font-size:0.9rem">${f.last_activity ? new Date(f.last_activity).toLocaleString('ru-RU') : '-'}</td>
            <td>
                <div style="display:flex; gap:4px">
                    <button class="view-btn" onclick="viewFamily('${f.id}')">👁️</button>
                    <button class="block-btn ${getFamilyBlockButtonClass(f.isBlocked)}" onclick="toggleBlock('${f.id}', ${!f.isBlocked})">${getFamilyBlockButtonLabel(f.isBlocked)}</button>
                </div>
            </td>`;
}

function showFamilyState(message) {
    if (!familiesLoadingState || !familiesTable) return;
    familiesLoadingState.textContent = message;
    familiesLoadingState.hidden = false;
    familiesTable.hidden = true;
}

function hideFamilyState() {
    if (!familiesLoadingState || !familiesTable) return;
    familiesLoadingState.hidden = true;
    familiesTable.hidden = false;
}

function appendFamilyRows(rows) {
    const tbody = document.getElementById('families-tbody');
    if (!tbody) return;
    rows.forEach((family) => {
        const tr = document.createElement('tr');
        tr.innerHTML = buildFamilyRowHtml(family);
        tbody.appendChild(tr);
    });
}

function renderFamilies() {
    if (!familiesLoadingState || !familiesTable) return;
    const tbody = document.getElementById('families-tbody');
    if (tbody) tbody.innerHTML = '';
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
            statusChips.forEach(node => node.classList.toggle('active', node === chip));
            renderFamilies();
        });
    });

    sortChips.forEach((chip) => {
        chip.addEventListener('click', () => {
            familiesViewState.sort = chip.dataset.sort || 'created';
            sortChips.forEach(node => node.classList.toggle('active', node === chip));
            renderFamilies();
        });
    });

    if (searchInput) {
        searchInput.addEventListener('input', () => {
            familiesViewState.search = searchInput.value.trim();
            renderFamilies();
        });
    }
}

async function viewFamily(familyId) {
    const modal = document.getElementById('family-modal');
    if (!modal) return;
    modal.classList.add('active');
    document.getElementById('modal-title').textContent = 'Загрузка...';
    document.getElementById('modal-body').innerHTML = '<div class="loading">Загрузка данных...</div>';
    try {
        const res = await fetch(`/api/super/family/${familyId}/data`);
        if (res.ok) renderFamilyDetails(await res.json());
        else document.getElementById('modal-body').innerHTML = '<p style="color: red;">Ошибка загрузки</p>';
    } catch (err) {
        document.getElementById('modal-body').innerHTML = '<p style="color: red;">Ошибка связи</p>';
    }
}

function toggleBlock(familyId, shouldBlock) {
    if (!confirm(shouldBlock ? 'Заблокировать?' : 'Разблокировать?')) return;
    fetch(`/api/super/family/${familyId}/block`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ isBlocked: shouldBlock })
    })
        .then((res) => {
            if (res.ok) loadFamilies();
            else alert('Ошибка');
        })
        .catch(() => alert('Ошибка связи'));
}

function copyMagicLink(token) {
    if (!token) return alert('Token missing');
    navigator.clipboard.writeText(`${window.location.origin}/login-child/${token}`).then(() => alert('Link copied')).catch(() => alert('Failed to copy'));
}

function regenerateToken(familyId, childId) {
    if (!confirm('Regenerate link?')) return;
    const url = childId ? `/api/super/child/${childId}/regenerate-token` : `/api/super/family/${familyId}/regenerate-token`;
    fetch(url, { method: 'POST' })
        .then((res) => {
            if (res.ok) { alert('Token regenerated'); loadFamilies(); } else alert('Failed');
        })
        .catch(() => alert('Error'));
}

document.getElementById('modal-close')?.addEventListener('click', () => document.getElementById('family-modal')?.classList.remove('active'));
document.getElementById('family-modal')?.addEventListener('click', (e) => {
    if (e.target.id === 'family-modal') document.getElementById('family-modal')?.classList.remove('active');
});

export function initFamiliesPanel() {
    setupFamiliesControls();
    window.viewFamily = viewFamily;
    window.toggleBlock = toggleBlock;
    window.copyMagicLink = copyMagicLink;
    window.regenerateToken = regenerateToken;
    loadFamilies();
}
