/** @file System dashboard module for super-admin tabs */
const systemHint = document.getElementById('system-hint');
const systemCards = {
    cpu: document.getElementById('system-cpu-value'),
    memory: document.getElementById('system-memory-value'),
    uptime: document.getElementById('system-uptime-value'),
    db: document.getElementById('system-db-value')
};
const httpStateEl = document.getElementById('http-state');
const httpBody = document.getElementById('http-metrics-body');
const systemHttpBadge = document.getElementById('system-http-badge');
const httpFilterContainer = document.getElementById('http-filter-levels');
const httpFilterButtons = httpFilterContainer ? httpFilterContainer.querySelectorAll('[data-http-filter]') : [];
const logsStateEl = document.getElementById('logs-state');
const logsListEl = document.getElementById('logs-list');
const logsLevelContainer = document.getElementById('logs-levels');
const logsLevelButtons = logsLevelContainer ? logsLevelContainer.querySelectorAll('[data-logs-level]') : [];
const logsLimitInput = document.getElementById('logs-limit');
const logsRefreshBtn = document.getElementById('logs-refresh');

const systemState = {
    overviewPollId: null,
    httpPollId: null,
    logsPollId: null,
    level: 'all',
    httpFilter: 'all',
    limit: clampNumber(Number(logsLimitInput?.value), 1, 500)
};
const OVERVIEW_DB_POLL_INTERVAL = 10000;
const HTTP_POLL_INTERVAL = 15000;
const LOGS_POLL_INTERVAL = 15000;

function clampNumber(value, min, max) {
    if (Number.isNaN(value)) return min;
    return Math.min(Math.max(value, min), max);
}

function formatBytes(bytes) {
    if (typeof bytes !== 'number') return '—';
    return `${Math.round(bytes / 1024 / 1024)} МБ`;
}

function formatDuration(seconds) {
    if (typeof seconds !== 'number') return '—';
    const hrs = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    return `${hrs}ч ${mins}м`;
}

function setCardText(key, value) {
    const el = systemCards[key];
    if (!el) return;
    el.textContent = value;
}

function setSystemHint(message, visible = true) {
    if (!systemHint) return;
    systemHint.textContent = message;
    systemHint.hidden = !visible;
}

function updateSystemSectionState(stateEl, status, message) {
    if (!stateEl) return;
    stateEl.textContent = message || '';
    stateEl.hidden = status === 'loaded';
    stateEl.classList.remove('panel-state--loading', 'panel-state--error', 'panel-state--empty');
    if (status === 'loading') stateEl.classList.add('panel-state--loading');
    if (status === 'empty') stateEl.classList.add('panel-state--empty');
    if (status === 'error') stateEl.classList.add('panel-state--error');
}

function cpuCardText(os) {
    return `${os.loadAvg1 ?? '—'} / ${os.loadAvg5 ?? '—'} / ${os.loadAvg15 ?? '—'}`;
}

function memoryCardText(processStats) {
    return `${formatBytes(processStats.rssBytes)} / ${formatBytes(processStats.heapUsedBytes)}`;
}

function uptimeCardText(processStats) {
    return formatDuration(processStats.uptimeSec ?? processStats.uptime ?? 0);
}

function dbCardText(db) {
    if (db.connected) {
        return db.pingMs ? `${db.pingMs}ms` : '—';
    }
    return db.lastError ? 'недоступна' : '—';
}

function updateOverviewCards(data) {
    const processStats = data?.process || {};
    const os = data?.os || {};
    setCardText('cpu', cpuCardText(os));
    setCardText('memory', memoryCardText(processStats));
    setCardText('uptime', uptimeCardText(processStats));
}

function updateDbCard(data) {
    const db = data?.db || {};
    setCardText('db', dbCardText(db));
}

async function fetchJson(url) {
    const res = await fetch(url);
    const payload = await res.json().catch(() => ({}));
    if (!res.ok) {
        const message = payload?.error || payload?.message || `status ${res.status}`;
        throw new Error(message);
    }
    return payload;
}

async function loadSystemOverview() {
    setSystemHint('Загрузка метрик...', true);
    try {
        const data = await fetchJson('/api/super/system/overview');
        updateOverviewCards(data);
        setSystemHint('', false);
    } catch (err) {
        console.error('System overview error', err);
        updateOverviewCards(null);
        setSystemHint('Метрики недоступны', true);
    }
}

async function loadDbHealth() {
    try {
        const data = await fetchJson('/api/super/system/db');
        updateDbCard(data);
    } catch (err) {
        console.error('DB health error', err);
        updateDbCard(null);
        setSystemHint('Часть системных метрик недоступна', true);
    }
}

function renderHttpMetrics(endpoints) {
    if (!httpBody) return;
    httpBody.innerHTML = '';
    endpoints.forEach((item) => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${item.method || '-'}</td>
            <td>${item.path || '-'}</td>
            <td>${item.count ?? 0}</td>
            <td>${item.errors ?? 0}</td>
            <td>${item.avgDurationMs ?? '—'}</td>
        `;
        httpBody.appendChild(tr);
    });
}

function setHttpBadge(summary) {
    if (!systemHttpBadge) return;
    const errorRate = summary?.errorRatePct;
    const errorRateText = typeof errorRate === 'number' ? `${errorRate}%` : '—';
    systemHttpBadge.textContent = `ошибки ${summary?.errorsTotal ?? '—'} (${errorRateText})`;
}

function updateHttpSection(endpoints) {
    const visibleEndpoints = systemState.httpFilter === 'errors'
        ? endpoints.filter((item) => (item.errors || 0) > 0)
        : endpoints;
    if (visibleEndpoints.length > 0) {
        renderHttpMetrics(visibleEndpoints);
        updateSystemSectionState(httpStateEl, 'loaded', '');
        return;
    }
    updateSystemSectionState(httpStateEl, 'empty', 'Данных нет');
}

async function loadHttpMetrics() {
    updateSystemSectionState(httpStateEl, 'loading', 'Загрузка HTTP-метрик...');
    if (httpBody) httpBody.innerHTML = '';
    if (systemHttpBadge) systemHttpBadge.textContent = 'ошибки —';
    try {
        const data = await fetchJson('/api/super/system/http-metrics');
        const endpoints = data.topEndpoints || [];
        updateHttpSection(endpoints);
        setHttpBadge(data.summary);
    } catch (err) {
        console.error('HTTP metrics error', err);
        updateSystemSectionState(httpStateEl, 'error', 'Не удалось загрузить HTTP-метрики');
    }
}

function renderLogEntries(logs) {
    if (!logsListEl) return;
    logsListEl.innerHTML = '';
    logs.forEach((log) => {
        const li = document.createElement('li');
        li.className = 'system-logs-item';
        const timestamp = log.ts ? new Date(log.ts).toLocaleString('ru-RU') : '—';
        li.innerHTML = `
            <div>${log.msg || '—'}</div>
            <div class="system-logs-item__meta">${timestamp} · ${log.level || '—'} · ${log.module || '—'} · req ${log.reqId || '-'}</div>
        `;
        logsListEl.appendChild(li);
    });
    logsListEl.hidden = logs.length === 0;
}

async function loadSystemLogs() {
    updateSystemSectionState(logsStateEl, 'loading', 'Загрузка логов...');
    if (logsListEl) logsListEl.innerHTML = '';
    const level = systemState.level;
    const limit = clampNumber(Number(logsLimitInput?.value), 1, 500);
    systemState.limit = limit;
    if (logsLimitInput) logsLimitInput.value = limit;
    try {
        const data = await fetchJson(`/api/super/system/logs?level=${level}&limit=${limit}`);
        const logs = data.logs || [];
        if (logs.length) {
            renderLogEntries(logs);
            updateSystemSectionState(logsStateEl, 'loaded', '');
        } else {
            updateSystemSectionState(logsStateEl, 'empty', 'Логов пока нет');
            if (logsListEl) logsListEl.hidden = true;
        }
    } catch (err) {
        console.error('Logs error', err);
        updateSystemSectionState(logsStateEl, 'error', 'Ошибка загрузки логов');
        if (logsListEl) logsListEl.hidden = true;
    }
}

function loadOverviewAndDbPanels() {
    loadSystemOverview();
    loadDbHealth();
}

function stopSystemPolling() {
    if (systemState.overviewPollId) {
        clearInterval(systemState.overviewPollId);
        systemState.overviewPollId = null;
    }
    if (systemState.httpPollId) {
        clearInterval(systemState.httpPollId);
        systemState.httpPollId = null;
    }
    if (systemState.logsPollId) {
        clearInterval(systemState.logsPollId);
        systemState.logsPollId = null;
    }
}

function activateSystemTab() {
    stopSystemPolling();
    loadOverviewAndDbPanels();
    loadHttpMetrics();
    loadSystemLogs();
    systemState.overviewPollId = setInterval(loadOverviewAndDbPanels, OVERVIEW_DB_POLL_INTERVAL);
    systemState.httpPollId = setInterval(loadHttpMetrics, HTTP_POLL_INTERVAL);
    systemState.logsPollId = setInterval(loadSystemLogs, LOGS_POLL_INTERVAL);
}

function initializeSystemControls() {
    httpFilterButtons.forEach((btn) => {
        btn.addEventListener('click', () => {
            systemState.httpFilter = btn.dataset.httpFilter || 'all';
            httpFilterButtons.forEach((node) => node.classList.toggle('active', node === btn));
            loadHttpMetrics();
        });
    });
    logsLevelButtons.forEach((btn) => {
        btn.addEventListener('click', () => {
            systemState.level = btn.dataset.logsLevel || 'all';
            logsLevelButtons.forEach((node) => node.classList.toggle('active', node === btn));
            loadSystemLogs();
        });
    });
    if (logsLimitInput) {
        logsLimitInput.addEventListener('change', () => {
            const value = clampNumber(Number(logsLimitInput.value), 1, 500);
            systemState.limit = value;
            logsLimitInput.value = value;
            loadSystemLogs();
        });
    }
    if (logsRefreshBtn) {
        logsRefreshBtn.addEventListener('click', () => {
            loadOverviewAndDbPanels();
            loadHttpMetrics();
            loadSystemLogs();
        });
    }
}

export function initSystemPanel() {
    initializeSystemControls();
    const activeSystemTab = document.getElementById('tab-system');
    if (activeSystemTab?.classList.contains('active')) {
        activateSystemTab();
    }
}

export function deactivateSystemTab() {
    stopSystemPolling();
}

export { activateSystemTab };
