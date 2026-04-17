/** @file Websocket frontend UI module */
import { state } from './state.js';
import { refreshFromServerAndRender } from './main-init.js';
import { showToast } from './utils.js';

let socket = null;
let reconnectTimer = null;

async function fetchWebSocketToken() {
    try {
        const response = await fetch('/api/ws-token', { credentials: 'same-origin' });
        if (!response.ok) {
            throw new Error(`Token fetch failed with status ${response.status}`);
        }
        const data = await response.json();
        return data.token;
    } catch (err) {
        console.error('WS token fetch failed:', err);
        return null;
    }
}

export async function initializeWebSocket() {
    if (socket) return;

    const token = await fetchWebSocketToken();
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const query = token ? `?token=${encodeURIComponent(token)}` : '';
    const wsUrl = `${protocol}//${window.location.host}/ws${query}`;

    try {
        socket = new WebSocket(wsUrl);

        socket.onopen = () => {
            if (reconnectTimer) {
                clearTimeout(reconnectTimer);
                reconnectTimer = null;
            }
        };

        socket.onmessage = (event) => {
            try {
                const message = JSON.parse(event.data);
                void handleWSMessage(message);
            } catch (err) {
                console.error('Failed to parse WS message:', err);
            }
        };

        socket.onclose = () => {
            socket = null;
            // Reconnect after 5 seconds
            if (!reconnectTimer) {
                reconnectTimer = setTimeout(initializeWebSocket, 5000);
            }
        };

        socket.onerror = (err) => {
            console.error('WS error:', err);
            socket.close();
        };
    } catch (err) {
        console.error('Failed to initialize WS:', err);
    }
}

function getPendingRequestsCount() {
    return (state.requests || []).filter((item) => item.status === 'pending').length;
}

function showChildUpdateForAdmin(pendingDelta) {
    if (pendingDelta > 0) {
        showToast(`Новая заявка: +${pendingDelta}`, 'success');
        return;
    }
    showToast('Данные обновлены ребенком', 'info');
}

async function handleDataUpdatedMessage(data) {
    const beforePending = getPendingRequestsCount();
    await refreshFromServerAndRender(false);
    const afterPending = getPendingRequestsCount();
    const pendingDelta = afterPending - beforePending;

    if (data.by === 'admin' && !state.isAdmin) {
        showToast('Данные обновлены родителем', 'info');
        return;
    }

    if (data.by === 'child' && state.isAdmin) {
        showChildUpdateForAdmin(pendingDelta);
    }
}

async function handleWSMessage(message) {
    const { type, data } = message;

    switch (type) {
        case 'DATA_UPDATED':
            await handleDataUpdatedMessage(data);
            break;
        case 'CHILD_UPDATED':
            await refreshFromServerAndRender(false);
            break;
        case 'CHILD_DELETED':
            if (!state.isAdmin && state.currentChildId === data.childId) {
                window.location.reload();
            } else {
                await refreshFromServerAndRender(false);
            }
            break;
        default:
            break;
    }
}
