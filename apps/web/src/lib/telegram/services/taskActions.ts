import { getContext, setContext } from 'svelte';
import { earnCoins, requestCoins, requestCoinsWithNote, saveChildGroupOrder } from '$lib/services/api';
import { applyDataSnapshot, refreshData } from '$lib/services/bootstrap';
import { scheduleSave } from '$lib/services/save';
import type { ApiActionResult } from '$lib/services/api';
import { appStore, type Task } from '$lib/stores/app';

export type TaskActionInput = { id?: string | number; taskId?: string | number; childId?: string | number; note?: string | null; [key: string]: unknown };
export type TaskActions = {
    request: (input: TaskActionInput) => Promise<ApiActionResult>;
    complete: (input: TaskActionInput) => Promise<unknown | null>;
    saveGroups: (childId: unknown, groups: string[], hiddenGroups: string[]) => Promise<ApiActionResult>;
    persist: () => Promise<boolean>;
    applySnapshot: (snapshot: Record<string, unknown>) => void;
    refresh: (showSuccess?: boolean) => Promise<boolean>;
    saveTask: (task: Task | null, payload: Partial<Task>) => Promise<ApiActionResult>;
};
const KEY = Symbol('earnit-kids-task-actions');
export function createProductionTaskActions(): TaskActions { return {
    request: ({ taskId, id, childId, note }) => note ? requestCoinsWithNote(taskId ?? id, note, childId) : requestCoins(taskId ?? id, childId),
    complete: ({ taskId, id, childId }) => earnCoins(taskId ?? id, childId),
    saveGroups: (childId, groups, hiddenGroups) => saveChildGroupOrder(childId, 'tasks', groups, hiddenGroups),
    persist: async () => { await scheduleSave(); return true; },
    applySnapshot: applyDataSnapshot,
    refresh: refreshData,
    saveTask: async (task, payload) => { const next = task ? $replaceTask(task, payload) : { ...payload, id: `task-${Date.now()}` } as Task; appStore.setState({ tasks: task ? appStoreValue().map((item) => item.id === task.id ? next : item) : [...appStoreValue(), next] }); await scheduleSave(); return { ok: true, data: null }; },
}; }
function appStoreValue(): Task[] { let value: Task[] = []; const unsubscribe = appStore.subscribe((state) => { value = state.tasks; }); unsubscribe(); return value; }
function $replaceTask(task: Task, payload: Partial<Task>): Task { return { ...task, ...payload }; }
export function provideTaskActions(actions: TaskActions): TaskActions { setContext(KEY, actions); return actions; }
export function useTaskActions(): TaskActions { return getContext<TaskActions | undefined>(KEY) ?? createProductionTaskActions(); }
