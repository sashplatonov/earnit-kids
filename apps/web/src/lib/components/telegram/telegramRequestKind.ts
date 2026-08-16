import type { Request } from '$lib/stores/app';

export type RequestKind = 'task' | 'reward';

/**
 * Shared Mini App request-kind mapper. Parent and child request views must
 * classify a request as a task vs a reward through this single source of truth
 * so the two surfaces never diverge.
 */
export function requestKind(request: Request): RequestKind {
    return request.requestType === 'shop_purchase' ? 'reward' : 'task';
}
