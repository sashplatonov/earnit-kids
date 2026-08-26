import { getContext, setContext } from 'svelte';
import { approveRequest, rejectRequest, deleteRequest } from '$lib/services/api';
import { refreshData } from '$lib/services/bootstrap';
export type RequestActions = { approve: (id: unknown, childId?: unknown) => Promise<unknown>; reject: (id: unknown, childId?: unknown) => Promise<unknown>; cancel: (id: unknown, childId?: unknown) => Promise<unknown>; refresh: (showSuccess?: boolean) => Promise<boolean>; };
const KEY = Symbol('earnit-kids-request-actions');
export function createProductionRequestActions(): RequestActions { return { approve: approveRequest, reject: rejectRequest, cancel: deleteRequest, refresh: refreshData }; }
export function provideRequestActions(actions: RequestActions): RequestActions { setContext(KEY, actions); return actions; }
export function useRequestActions(): RequestActions { return getContext<RequestActions | undefined>(KEY) ?? createProductionRequestActions(); }
