import type { AppState } from '$lib/stores/app';

export type TelegramParentView = 'requests' | 'tasks' | 'rewards' | 'child';

export function initialParentView(state: Pick<AppState, 'requests'>): TelegramParentView {
    return state.requests.some((request) => request.status === 'pending') ? 'requests' : 'tasks';
}
