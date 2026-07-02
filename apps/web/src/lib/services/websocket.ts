/**
 * WebSocket service — replaces legacy websocket.js
 * Fetches a token from /api/ws-token, then opens a WebSocket and reconnects on close.
 */
import { refreshData } from './bootstrap';

type WsEventType = 'update' | 'notification' | string;
type WsListener = (payload: unknown) => void;

const listeners = new Map<WsEventType, Set<WsListener>>();

let socket: WebSocket | null = null;
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
let stopped = false;
let refreshInFlight = false;
let refreshQueued = false;

function clearReconnect() {
    if (reconnectTimer != null) {
        clearTimeout(reconnectTimer);
        reconnectTimer = null;
    }
}

/**
 * Coalesced refresh — if a refresh is already in flight, queue another one
 * to run immediately after. Multiple 'update' events during an in-flight
 * refresh result in at most one additional refresh.
 */
async function coalescedRefresh(): Promise<void> {
    if (refreshInFlight) {
        refreshQueued = true;
        return;
    }
    refreshInFlight = true;
    refreshQueued = false;
    try {
        await refreshData();
    } finally {
        refreshInFlight = false;
        if (refreshQueued) {
            refreshQueued = false;
            // Schedule on microtask to avoid unbounded recursion
            queueMicrotask(() => { void coalescedRefresh(); });
        }
    }
}

async function getWsToken(): Promise<string | null> {
    try {
        const res = await fetch('/api/ws-token', { credentials: 'same-origin' });
        if (!res.ok) return null;
        const data = await res.json() as { token?: string };
        return data.token ?? null;
    } catch {
        return null;
    }
}

async function connect() {
    if (stopped) return;
    clearReconnect();

    const token = await getWsToken();
    if (!token) {
        scheduleReconnect();
        return;
    }

    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    const url = `${proto}://${location.host}/ws?token=${encodeURIComponent(token)}`;

    socket = new WebSocket(url);

    socket.addEventListener('message', (event) => {
        try {
            const parsed = JSON.parse(event.data as string) as { type?: string; payload?: unknown };
            const type = parsed.type ?? 'message';
            emit(type, parsed.payload ?? parsed);
            if (type === 'update') void coalescedRefresh();
        } catch { /* ignore non-JSON */ }
    });

    socket.addEventListener('close', () => {
        socket = null;
        if (!stopped) scheduleReconnect();
    });

    socket.addEventListener('error', () => {
        socket?.close();
    });
}

function scheduleReconnect() {
    clearReconnect();
    reconnectTimer = setTimeout(() => { void connect(); }, 5000);
}

function emit(type: string, payload: unknown) {
    listeners.get(type)?.forEach(fn => fn(payload));
    listeners.get('*')?.forEach(fn => fn({ type, payload }));
}

export function startWebSocket() {
    stopped = false;
    void connect();
}

export function stopWebSocket() {
    stopped = true;
    clearReconnect();
    socket?.close();
    socket = null;
}

export function onWsEvent(type: WsEventType, fn: WsListener) {
    if (!listeners.has(type)) listeners.set(type, new Set());
    listeners.get(type)!.add(fn);
    return () => listeners.get(type)?.delete(fn);
}
