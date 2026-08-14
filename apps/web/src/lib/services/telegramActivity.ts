import { fetchWithCsrf } from './api';
import type { HistoryEntry, Request } from '$lib/stores/app';

export type TelegramPage<T> = { items: T[]; total: number; page: number; limit: number };

async function loadPage<T>(url: string): Promise<TelegramPage<T>> {
    const response = await fetchWithCsrf(url);
    if (!response.ok) throw new Error('Activity could not be loaded');
    const payload = await response.json() as Partial<TelegramPage<T>>;
    return { items: Array.isArray(payload.items) ? payload.items : [], total: Number(payload.total ?? 0), page: Number(payload.page ?? 1), limit: Number(payload.limit ?? 20) };
}

export const loadTelegramHistory = (childId: string | number, page = 1, limit = 20) =>
    loadPage<HistoryEntry>(`/api/history?childId=${encodeURIComponent(String(childId))}&page=${page}&limit=${limit}`);

export const loadTelegramRequests = (page = 1, limit = 20) =>
    loadPage<Request>(`/api/requests?page=${page}&limit=${limit}`);
