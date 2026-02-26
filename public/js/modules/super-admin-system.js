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
const logsStateEl = document.getElementById('logs-state');
const logsListEl = document.getElementById('logs-list');
const logsLevelContainer = document.getElementById('logs-levels');
const logsLevelButtons = logsLevelContainer ? logsLevelContainer.querySelectorAll('[data-logs-level]') : [];
const logsLimitInput = document.getElementById('logs-limit');
const logsRefreshBtn = document.getElementById('logs-refresh');

const systemState = {
    pollId: null,
    level: 'error',
    limit: clampNumber(Number(logsLimitInput?.value), 1, 500)
};
const SYSTEM_POLL_INTERVAL = 15000;

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

function memoryCardText(process) {
    return `${formatBytes(process.rssBytes)} / ${formatBytes(process.heapUsedBytes)}`;
}

function uptimeCardText(process) {
    return formatDuration(process.uptimeSec ?? process.uptime ?? 0);
}

function dbCardText(db) {
    if (db.connected) {
        const ping = db.pingMs ? `${db.pingMs}ms` : '—';
        const replicaStatus = db.reserveConnected ? 'реплика OK' : 'реплика недоступна';
        return `${ping} · ${replicaStatus}`;
    }
    return '—';
}

function updateSystemCards(data) {
    const process = data?.process || {};
    const os = data?.os || {};
    const db = data?.db || {};
    setCardText('cpu', cpuCardText(os));
    setCardText('memory', memoryCardText(process));
    setCardText('uptime', uptimeCardText(process));
    setCardText('db', dbCardText(db));
}

async function fetchJson(url) {
    const res = await fetch(url);
    if (!res.ok) throw new Error(`status ${res.status}`);
    return res.json();
}

async function loadSystemOverview() {
    setSystemHint('Загрузка метрик...', true);
    try {
        const data = await fetchJson('/api/super/system/overview');
        updateSystemCards(data);
        setSystemHint('', false);
    } catch (err) {
        console.error('System overview error', err);
        updateSystemCards(null);
        setSystemHint('Метрики недоступны', true);
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

async function loadHttpMetrics() {
    updateSystemSectionState(httpStateEl, 'loading', 'Загрузка HTTP-метрик...');
    if (httpBody) httpBody.innerHTML = '';
    if (systemHttpBadge) systemHttpBadge.textContent = 'ошибки —';
    try {
        const data = await fetchJson('/api/super/system/http-metrics');
        const endpoints = data.topEndpoints || [];
        if (endpoints.length) {
            renderHttpMetrics(endpoints);
            updateSystemSectionState(httpStateEl, 'loaded', '');
        } else {
            updateSystemSectionState(httpStateEl, 'empty', 'Данных нет');
        }
        if (systemHttpBadge) systemHttpBadge.textContent = `ошибки ${data?.summary?.errorsTotal ?? '—'}`;
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

function loadSystemPanels() {
    loadSystemOverview();
    loadHttpMetrics();
    loadSystemLogs();
}

function stopSystemPolling() {
    if (systemState.pollId) {
        clearInterval(systemState.pollId);
        systemState.pollId = null;
    }
}

function activateSystemTab() {
    stopSystemPolling();
    loadSystemPanels();
    systemState.pollId = setInterval(loadSystemPanels, SYSTEM_POLL_INTERVAL);
}

function initializeSystemControls() {
    logsLevelButtons.forEach((btn) => {
        btn.addEventListener('click', () => {
            systemState.level = btn.dataset.logsLevel || 'error';
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
            loadHttpMetrics();
            loadSystemLogs();
        });
    }
}

export function initSystemPanel() {
    initializeSystemControls();
}

export function deactivateSystemTab() {
    stopSystemPolling();
}

export { activateSystemTab };
