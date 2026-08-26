import { getContext, setContext } from 'svelte';
import { requestItem, requestItemWithNote } from '$lib/telegram/services/shopApi';
import type { ApiActionResult } from '$lib/services/api';

export type RewardRequestActionInput = {
    itemId: number | string;
    childId: string | number | null;
    note: string | null;
};

export type RewardRequestActions = {
    request: (input: RewardRequestActionInput) => Promise<ApiActionResult>;
};

const REWARD_REQUEST_ACTIONS_CONTEXT = Symbol('earnit-kids-reward-request-actions');

export function createProductionRewardRequestActions(): RewardRequestActions {
    return {
        request: ({ itemId, childId, note }) =>
            note ? requestItemWithNote(itemId, note, childId) : requestItem(itemId, childId),
    };
}

export function provideRewardRequestActions(actions: RewardRequestActions): RewardRequestActions {
    setContext(REWARD_REQUEST_ACTIONS_CONTEXT, actions);
    return actions;
}

export function useRewardRequestActions(): RewardRequestActions {
    return getContext<RewardRequestActions | undefined>(REWARD_REQUEST_ACTIONS_CONTEXT)
        ?? createProductionRewardRequestActions();
}
