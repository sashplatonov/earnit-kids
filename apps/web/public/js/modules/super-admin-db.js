/** @file Super Admin Db frontend UI module */
/**
 * Super Admin Database Management Module
 */

import { fetchWithCsrf } from './api.js';

const PANEL_STATE_CLASSES = ['panel-state--loading', 'panel-state--error'];

function setDbPanelState(message, variant) {
    const panelState = document.getElementById('db-panel-state');
    if (!panelState) return;
    panelState.textContent = message;
    panelState.hidden = variant === 'hidden';
    panelState.classList.remove(...PANEL_STATE_CLASSES);
    if (variant === 'loading') panelState.classList.add('panel-state--loading');
    if (variant === 'error') panelState.classList.add('panel-state--error');
}

export function setDbStatus(msg, type) {
    const el = document.getElementById('db-status-msg');
    if (!el) return;
    if (!msg) {
        el.hidden = true;
        return;
    }
    el.hidden = false;
    el.textContent = msg;

    const styles = {
        error: { bg: '#fee2e2', color: '#ef4444', border: '1px solid #fca5a5' },
        success: { bg: '#dcfce7', color: '#10b981', border: '1px solid #86efac' },
        info: { bg: '#eff6ff', color: '#3b82f6', border: '1px solid #93c5fd' }
    };

    const s = styles[type] || styles.info;
    el.style.background = s.bg;
    el.style.color = s.color;
    el.style.border = s.border;
}

function handleDbSuccess(pingMs) {
    const ping = pingMs ? `${pingMs}ms` : '—';
    setDbStatus(`Ping ${ping}`, 'success');
    setDbPanelState('', 'hidden');
}

function handleDbError(reason) {
    const message = reason || 'Невозможно проверить базу';
    setDbStatus(`Ошибка: ${message}`, 'error');
    setDbPanelState('Невозможно получить статус базы данных', 'error');
}

async function fetchDbPayload() {
    const res = await fetch('/api/super/system/db');
    if (!res.ok) {
        throw new Error('Не удалось получить статус');
    }
    return res.json();
}

export async function refreshDbPanelStatus() {
    setDbPanelState('Проверяем доступность базы данных...', 'loading');
    setDbStatus('', 'info');
    try {
        const payload = await fetchDbPayload();
        const db = payload?.db;
        return db?.connected
            ? handleDbSuccess(db.pingMs)
            : handleDbError(payload?.error || db?.lastError);
    } catch (err) {
        handleDbError(err.message);
    }
}

export async function handleRestore(file) {
    if (!file) return;
    setDbStatus('Восстановление...', 'info');

    try {
        const res = await fetchWithCsrf('/api/super/db-restore', {
            method: 'POST',
            body: file,
            headers: { 'Content-Type': 'application/octet-stream' }
        });

        const result = await res.json();
        if (res.ok && result.success) {
            setDbStatus('Успешно! Перезагрузка...', 'success');
            setTimeout(() => window.location.reload(), 2000);
        } else {
            setDbStatus('Ошибка: ' + (result.error || 'Unknown'), 'error');
        }
    } catch (err) {
        setDbStatus('Ошибка связи', 'error');
    }
}
