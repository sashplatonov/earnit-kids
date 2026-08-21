import type { Request } from '$lib/stores/app';

export type RequestKind = 'task' | 'reward';

export const REWARD_REQUEST_TYPE = 'shop_purchase';

/**
 * Shared Mini App request-kind mapper. Parent and child request views must
 * classify a request as a task vs a reward through this single source of truth
 * so the two surfaces never diverge.
 */
export function requestKind(request: Request): RequestKind {
    return request.requestType === REWARD_REQUEST_TYPE ? 'reward' : 'task';
}
