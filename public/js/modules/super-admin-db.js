/** @file Super Admin Db frontend UI module */
/**
 * Super Admin Database Management Module
 */

export function setDbStatus(msg, type) {
    const el = document.getElementById('db-status-msg');
    if (!el) return;
    el.style.display = 'block';
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

function setDbPanelState(message, variant) {
    const panelState = document.getElementById('db-panel-state');
    if (!panelState) return;
    panelState.textContent = message;
    if (variant === 'hidden') {
        panelState.hidden = true;
        return;
    }
    panelState.hidden = false;
    panelState.classList.remove('panel-state--loading', 'panel-state--error', 'panel-state--empty');
    if (variant === 'error') {
        panelState.classList.add('panel-state--error');
    } else {
        panelState.classList.add('panel-state--loading');
    }
}

export async function checkReserveStatus() {
    const statusEl = document.getElementById('reserve-db-status');
    const copyBtn = document.getElementById('pg-copy-reserve-btn');
    if (!statusEl || !copyBtn) return;

    setDbPanelState('Проверяем доступность резервной БД...', 'loading');
    try {
        const res = await fetch('/api/super/db-reserve-status');
        const data = await res.json();

        if (data.success) {
            statusEl.innerHTML = '<span style="color: #10b981;">✅ Резервная БД доступна</span>';
            copyBtn.disabled = false;
            setDbPanelState('', 'hidden');
        } else {
            statusEl.innerHTML = `<span style="color: #ef4444;">❌ ${data.error || 'Ошибка'}</span>`;
            copyBtn.disabled = true;
            statusEl.title = data.error || '';
            setDbPanelState('Резервная БД недоступна', 'error');
        }
    } catch (err) {
        statusEl.innerHTML = '<span style="color: #ef4444;">❌ Ошибка проверки</span>';
        copyBtn.disabled = true;
        setDbPanelState('Ошибка связи', 'error');
    }
}

export async function handleRestore(file) {
    if (!file) return;
    setDbStatus('Восстановление...', 'info');

    try {
        const res = await fetch('/api/super/db-restore', {
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

export async function handleCopyToReserve() {
    if (!confirm('Скопировать в резерв?')) return;
    setDbStatus('Копирование...', 'info');
    const btn = document.getElementById('pg-copy-reserve-btn');
    if (btn) btn.disabled = true;

    try {
        const res = await fetch('/api/super/db-copy-reserve', { method: 'POST' });
        const result = await res.json();
        if (res.ok && result.success) {
            setDbStatus('Успешно скопировано!', 'success');
        } else {
            setDbStatus('Ошибка: ' + (result.error || 'Unknown'), 'error');
        }
    } catch (err) {
        setDbStatus('Ошибка связи', 'error');
    } finally {
        if (btn) btn.disabled = false;
    }
}
