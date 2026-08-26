import { getContext, setContext } from 'svelte';
import { applyDataSnapshot, initializeFromServer, refreshData, switchChild } from '$lib/services/bootstrap';
import { loadTelegramHistory, type TelegramPage } from '$lib/services/telegramActivity';
import type { HistoryEntry } from '$lib/stores/app';

export type WorkspaceChildId = string | number;
export type WorkspaceSnapshot = Record<string, unknown>;

export type WorkspaceHistoryInput = {
    childId: WorkspaceChildId;
    page?: number;
    limit?: number;
};

export type WorkspaceActions = {
    initialize: () => Promise<boolean>;
    refresh: (showSuccess?: boolean) => Promise<boolean>;
    applySnapshot: (snapshot: WorkspaceSnapshot) => void;
    switchChild: (childId: WorkspaceChildId) => Promise<void>;
    loadHistory: (input: WorkspaceHistoryInput) => Promise<TelegramPage<HistoryEntry>>;
};

const WORKSPACE_ACTIONS_CONTEXT = Symbol('earnit-kids-workspace-actions');

export function createProductionWorkspaceActions(): WorkspaceActions {
    return {
        initialize: initializeFromServer,
        refresh: refreshData,
        applySnapshot: applyDataSnapshot,
        switchChild,
        loadHistory: ({ childId, page = 1, limit = 20 }) => loadTelegramHistory(childId, page, limit),
    };
}

export function provideWorkspaceActions(actions: WorkspaceActions): WorkspaceActions {
    setContext(WORKSPACE_ACTIONS_CONTEXT, actions);
    return actions;
}

export function useWorkspaceActions(): WorkspaceActions {
    return getContext<WorkspaceActions | undefined>(WORKSPACE_ACTIONS_CONTEXT)
        ?? createProductionWorkspaceActions();
}
