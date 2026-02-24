/** @file Ios Dev Fallback frontend UI module */
import { loadDataFromServer } from './api.js';
import { showMobileEventNotification } from './utils.js';

const POLL_INTERVAL_MS = 15000;
let pollTimer = null;
let lastSnapshot = null;
let onDataSync = null;

function isIosEnvironment() {
    if (typeof window === 'undefined') return false;

    const ua = window.navigator?.userAgent || '';
    const isIosUa = /iPad|iPhone|iPod/.test(ua);

    if (!window.Capacitor || typeof window.Capacitor.getPlatform !== 'function') {
        return isIosUa;
    }

    const platform = window.Capacitor.getPlatform();
    return platform === 'ios' || isIosUa;
}

function normalize(data) {
    if (!data) return null;
    return {
        isAdmin: Boolean(data.isAdmin),
        balance: Number(data.balance || 0),
        children: Array.isArray(data.children) ? data.children.map((child) => ({
            id: child.id,
            name: child.name || 'Ребенок',
            balance: Number(child.balance || 0)
        })) : [],
        requests: Array.isArray(data.requests) ? data.requests.map((item) => ({
            id: String(item.id),
            childId: item.childId,
            taskName: item.taskName || 'Заявка',
            coins: Number(item.coins || 0),
            status: item.status || 'pending'
        })) : [],
        history: Array.isArray(data.history) ? data.history.map((entry) => ({
            id: String(entry.id),
            childId: entry.childId,
            amount: Number(entry.amount || 0)
        })) : []
    };
}

function pendingIds(requests) {
    return new Set((requests || []).filter((item) => item.status === 'pending').map((item) => item.id));
}

function detectBalanceMessages(previous, current) {
    if (!previous || !current) return [];

    if (current.isAdmin) {
        const beforeMap = new Map((previous.children || []).map((child) => [String(child.id), child]));
        return (current.children || []).reduce((acc, child) => {
            const prev = beforeMap.get(String(child.id));
            if (!prev) return acc;

            const delta = Number(child.balance || 0) - Number(prev.balance || 0);
            if (delta === 0) return acc;

            acc.push(`${child.name}: ${delta > 0 ? '+' : ''}${delta} 🪙`);
            return acc;
        }, []);
    }

    const delta = Number(current.balance || 0) - Number(previous.balance || 0);
    if (delta === 0) return [];
    return [`${delta > 0 ? '+' : ''}${delta} 🪙`];
}

function detectRequestCreated(previous, current) {
    const beforePending = pendingIds(previous?.requests || []);
    return (current?.requests || [])
        .filter((item) => item.status === 'pending' && !beforePending.has(item.id))
        .map((item) => `${item.taskName}: ${item.coins} 🪙`);
}

function findMatchingHistory(request, newHistory) {
    return newHistory.some((entry) => {
        const sameChild = String(entry.childId || '') === String(request.childId || '');
        const sameAmount = Number(entry.amount || 0) === Number(request.coins || 0);
        return sameChild && sameAmount;
    });
}

function getAddedHistory(previous, current) {
    const prevHistoryIds = new Set((previous?.history || []).map(e => e.id));
    return (current?.history || []).filter(e => !prevHistoryIds.has(e.id));
}

function getRemovedPendingRequests(previous, current) {
    const beforePending = (previous?.requests || []).filter(i => i.status === 'pending');
    const afterPendingIds = pendingIds(current?.requests || []);
    return beforePending.filter(i => !afterPendingIds.has(i.id));
}

function detectRequestApproved(previous, current) {
    const removedPending = getRemovedPendingRequests(previous, current);
    if (!removedPending.length) return [];

    const newHistory = getAddedHistory(previous, current);
    if (!newHistory.length) return [];

    return removedPending
        .filter(request => findMatchingHistory(request, newHistory))
        .map(item => `${item.taskName}: ${item.coins} 🪙`);
}

function emitNotifications(previous, current) {
    const balanceMessages = detectBalanceMessages(previous, current);
    balanceMessages.forEach((message) => {
        showMobileEventNotification(message, 'info', 'Баланс изменен');
    });

    const createdMessages = detectRequestCreated(previous, current);
    createdMessages.forEach((message) => {
        showMobileEventNotification(message, 'success', 'Новая заявка');
    });

    const approvedMessages = detectRequestApproved(previous, current);
    approvedMessages.forEach((message) => {
        showMobileEventNotification(message, 'success', 'Заявка подтверждена');
    });
}

async function pollOnce() {
    const data = await loadDataFromServer();
    if (!data) return;

    const current = normalize(data);
    if (lastSnapshot) {
        emitNotifications(lastSnapshot, current);
    }
    lastSnapshot = current;

    if (typeof onDataSync === 'function') {
        await onDataSync(data);
    }
}

export function startIosDevFallback(initialData, syncHandler) {
    if (!isIosEnvironment()) return false;

    onDataSync = typeof syncHandler === 'function' ? syncHandler : null;
    lastSnapshot = normalize(initialData);

    if (pollTimer) clearInterval(pollTimer);
    pollTimer = setInterval(() => {
        void pollOnce();
    }, POLL_INTERVAL_MS);
    void pollOnce();

    const appPlugin = window.Capacitor?.Plugins?.App;
    if (appPlugin?.addListener) {
        appPlugin.addListener('appStateChange', (event) => {
            if (event?.isActive) void pollOnce();
        });
    }

    return true;
}

export function stopIosDevFallback() {
    if (pollTimer) {
        clearInterval(pollTimer);
        pollTimer = null;
    }
    onDataSync = null;
    lastSnapshot = null;
}
