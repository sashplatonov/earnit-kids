import type { HistoryEntry } from '$lib/stores/app';
import type { TelegramPage } from '$lib/services/telegramActivity';
import type { WorkspaceActions } from '$lib/features/workspace/workspaceActions';
import type { TaskActions } from '$lib/telegram/services/taskActions';
import type { RewardActions } from '$lib/telegram/services/rewardActions';
import type { RequestActions } from '$lib/telegram/services/requestActions';
import type { FamilyActions } from '$lib/telegram/services/familyActions';
import type { HistoryActions } from '$lib/telegram/services/historyActions';
import type { RewardRequestActions } from '$lib/telegram/services/rewardRequestActions';
import type { LiveCoinShopDemoActions, LiveCoinShopDemoSession } from './liveCoinShopDemoSession';

export type LiveCoinShopDemoWorkspace = {
    workspace: WorkspaceActions;
    tasks: TaskActions;
    rewards: RewardActions;
    requests: RequestActions;
    family: FamilyActions;
    history: HistoryActions;
    rewardRequests: RewardRequestActions;
};

const unavailable = (() => Promise.resolve(null)) as (...args: unknown[]) => Promise<null>;

function unwrap(result: Awaited<ReturnType<LiveCoinShopDemoActions['completeTask']>>): Record<string, unknown> | null {
    return result.ok && result.data ? result.data as unknown as Record<string, unknown> : null;
}

function pageFor(session: LiveCoinShopDemoSession, childId: string | number, page = 1, limit = 20): TelegramPage<HistoryEntry> {
    const items = session.snapshot().app.history.filter((entry) => String(entry.childId) === String(childId));
    const start = (page - 1) * limit;
    return { items: items.slice(start, start + limit), total: items.length, page, limit };
}

export function createLiveCoinShopDemoWorkspace(session: LiveCoinShopDemoSession): LiveCoinShopDemoWorkspace {
    const actions = session.actions;
    const refresh = async () => true;
    const workspace: WorkspaceActions = {
        initialize: async () => true,
        refresh,
        applySnapshot: () => {},
        switchChild: async (childId) => { await actions.selectChild(childId); },
        loadHistory: async ({ childId, page = 1, limit = 20 }) => pageFor(session, childId, page, limit),
    };
    const tasks: TaskActions = {
        request: ({ taskId, id, childId, note }) => actions.request({ itemId: taskId ?? id ?? '', childId: childId ?? null, note: note ?? null }),
        complete: async ({ taskId, id, childId }) => unwrap(await actions.completeTask({ id: taskId ?? id, childId })),
        saveGroups: async (_childId, groups, hiddenGroups) => {
            for (const group of hiddenGroups) await actions.setGroupVisibility(group, true);
            return { ok: true, data: null };
        },
        persist: async () => true,
        applySnapshot: () => {},
        refresh,
        saveTask: async (task, payload) => task
            ? actions.editTask({ id: task.id, ...payload })
            : actions.createTask(payload),
    } as TaskActions;
    const rewards: RewardActions = {
        buy: async ({ itemId, id, childId }) => {
            const item = session.snapshot().shopItems.find((entry) => String(entry.id) === String(itemId ?? id));
            return item ? unwrap(await actions.spendCoins({ id: item.id, childId, amount: item.price })) : null;
        },
        saveGroups: async (_childId, _groups, hiddenGroups) => {
            for (const group of hiddenGroups) await actions.setGroupVisibility(group, true);
            return { ok: true, data: null };
        },
        persist: async () => true,
        applySnapshot: () => {},
        refresh,
        saveReward: async (item, payload) => item
            ? actions.editReward({ id: item.id, ...payload })
            : actions.createReward(payload),
    } as RewardActions;
    const requests: RequestActions = {
        approve: (id) => actions.approveRequest(id as string | number),
        reject: (id) => actions.rejectRequest(id as string | number),
        cancel: (id) => actions.cancelRequest(id as string | number),
        refresh,
    };
    const family: FamilyActions = {
        selectChild: async (id: string | number) => { await actions.selectChild(id); },
        addChild: (name: string) => actions.addChild({ name }),
        setChildActive: unavailable,
        awardCoins: (id: string | number, amount: number, description?: string) => actions.awardCoins({ childId: id, amount, description }),
        setLocale: unavailable,
        refresh,
        getInactive: async () => [],
        getTelegram: unavailable,
        createTelegramInvite: unavailable,
        unlinkTelegram: unavailable,
        getMagicLinkStatus: unavailable,
        issueMagicLink: unavailable,
        revokeMagicLink: unavailable,
    } as unknown as FamilyActions;
    const history: HistoryActions = { load: async (childId, page, limit) => pageFor(session, childId, page, limit) };
    const rewardRequests: RewardRequestActions = { request: actions.request };
    return { workspace, tasks, rewards, requests, family, history, rewardRequests };
}
