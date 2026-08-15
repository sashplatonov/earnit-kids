/** Svelte store replacing legacy state.js singleton */
import { writable, derived } from 'svelte/store';
import type { MembershipPermission } from '$lib/types/auth';

export interface Task {
    id: number | string;
    name: string;
    coins: number;
    isActive?: boolean;
    groupName?: string | null;
    icon?: string | null;
    comment?: string | null;
    cueWhen?: string | null;
    cueAction?: string | null;
    moneyLimit?: number | null;
    frequency?: { period?: string; limit?: number } | null;
    ageMin?: number | null;
    ageMax?: number | null;
    lastCompletedAt?: string | null;
    periodProgress?: TaskPeriodProgress | null;
    [key: string]: unknown;
}

export interface TaskPeriodProgress {
    period: string;
    completed: number;
    pending: number;
    limit: number;
    remaining: number;
    available: boolean;
    windowStart: string;
    resetAt: string;
}

export interface ShopItem {
    id: number | string;
    name: string;
    price: number;
    isActive?: boolean;
    groupName?: string | null;
    icon?: string | null;
    comment?: string | null;
    moneyLimit?: number | null;
    frequency?: { period?: string; limit?: number } | null;
    ageMin?: number | null;
    ageMax?: number | null;
    lastPurchasedAt?: string | null;
    [key: string]: unknown;
}

export interface HistoryEntry {
    id: number | string;
    type: 'task_completed' | 'purchase' | 'admin' | 'earn' | 'spend';
    amount: number;
    description?: string | null;
    title?: string | null;
    groupName?: string | null;
    comment?: string | null;
    taskName?: string | null;
    itemName?: string | null;
    moneyAmount?: number | null;
    taskId?: number | string | null;
    itemId?: number | string | null;
    relatedId?: number | string | null;
    createdAt?: string | null;
    [key: string]: unknown;
}

export interface Request {
    id: number | string;
    requestType: string;
    taskId?: number | string | null;
    itemId?: number | string | null;
    title?: string | null;
    description?: string | null;
    comment?: string | null;
    note?: string | null;
    groupName?: string | null;
    taskName?: string | null;
    itemName?: string | null;
    taskGroup?: string | null;
    itemGroup?: string | null;
    taskComment?: string | null;
    itemComment?: string | null;
    coins?: number | null;
    moneyAmount?: number | null;
    createdAt?: string | null;
    status: string;
    childId?: number | string | null;
    childNickname?: string | null;
    amount?: number | null;
    [key: string]: unknown;
}

export interface Friend {
    id: number | string;
    nickname: string;
    balance?: number;
    [key: string]: unknown;
}

export interface Child {
    id: number | string;
    nickname: string;
    balance: number;
    rewardGoalItemId?: number | string | null;
    status?: 'ACTIVE' | 'INACTIVE' | string | null;
    monthlyLimit?: number;
    dailyCoinLimit?: number;
    dailyRewardLimit?: number;
    isPinSet?: boolean;
    ageMin?: number | null;
    ageMax?: number | null;
    theme?: string | null;
    taskGroupOrder?: string[];
    shopGroupOrder?: string[];
    childTaskGroupOrder?: string[];
    childShopGroupOrder?: string[];
    hiddenTaskGroupOrder?: string[];
    hiddenShopGroupOrder?: string[];
    [key: string]: unknown;
}

export interface AppState {
    isAdmin: boolean;
    role: string | null;
    permission: MembershipPermission | null;
    balance: number;
    rules: string | null;
    tasks: Task[];
    shopItems: ShopItem[];
    history: HistoryEntry[];
    requests: Request[];
    friends: Friend[];
    childNickname: string | null;
    isPinSet: boolean;
    familyId: string | null;
    monthlyLimit: number;
    dailyCoinLimit: number;
    baseData: { tasks: Task[]; products: ShopItem[] };
    catalog: { tasks: CatalogTaskTemplate[]; rewards: CatalogRewardTemplate[] };
    children: Child[];
    currentChildId: string | number | null;
    isLoading: boolean;
}

export interface CatalogTaskTemplate {
    id: string;
    title: string;
    comment?: string | null;
    coins: number;
    groupKey: string;
    groupName: string;
    semanticGraphicKey?: string | null;
    frequencyLimit?: number | null;
    frequencyPeriod?: string | null;
    minAge?: number | null;
    maxAge?: number | null;
    difficulty?: string | null;
    tags?: string[];
    active?: boolean;
    sortOrder?: number;
    [key: string]: unknown;
}

export interface CatalogRewardTemplate {
    id: string;
    title: string;
    comment?: string | null;
    price: number;
    groupKey: string;
    groupName: string;
    semanticGraphicKey?: string | null;
    frequencyLimit?: number | null;
    frequencyPeriod?: string | null;
    minAge?: number | null;
    maxAge?: number | null;
    difficulty?: string | null;
    tags?: string[];
    active?: boolean;
    sortOrder?: number;
    [key: string]: unknown;
}

const initialState: AppState = {
    isAdmin: false,
    role: null,
    permission: null,
    balance: 0,
    rules: null,
    tasks: [],
    shopItems: [],
    history: [],
    requests: [],
    friends: [],
    childNickname: null,
    isPinSet: false,
    familyId: null,
    monthlyLimit: 10000,
    dailyCoinLimit: 0,
    baseData: { tasks: [], products: [] },
    catalog: { tasks: [], rewards: [] },
    children: [],
    currentChildId: null,
    isLoading: true,
};

function createAppStore() {
    const { subscribe, set, update } = writable<AppState>(initialState);

    return {
        subscribe,
        set,
        update,
        setState(partial: Partial<AppState>) {
            update(s => ({ ...s, ...partial }));
        },
        updateBalance(amount: number) {
            update(s => ({ ...s, balance: s.balance + amount }));
        },
        reset() {
            set(initialState);
        },
    };
}

export const appStore = createAppStore();

/** Derived: pending requests count (for nav badge) */
export const pendingRequestsCount = derived(appStore, ($app) =>
    $app.requests.filter(r => r.status === 'pending').length
);
