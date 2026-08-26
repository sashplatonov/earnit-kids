import { get } from 'svelte/store';
import { appStore, type AppState, type Request } from '$lib/stores/app';
import { shopItems } from '$lib/telegram/stores/shopItems';
import type { ShopItem } from '$lib/telegram/stores/types';
import type { ApiActionResult } from '$lib/services/api';
import type { RewardRequestActionInput, RewardRequestActions } from '$lib/telegram/services/rewardRequestActions';

const DEMO_CHILD_ID = 'live-demo-child';
const AFFORDABLE_REWARD_ID = 'live-demo-reward-ice-cream';
const INACTIVE_REWARD_ID = 'live-demo-reward-inactive';
const UNAFFORDABLE_REWARD_ID = 'live-demo-reward-trip';

const initialChild: AppState['children'][number] = {
    id: DEMO_CHILD_ID,
    nickname: 'Mia',
    balance: 75,
    status: 'ACTIVE',
    shopGroupOrder: ['Treats', 'Experiences'],
};

const initialItems: ShopItem[] = [
    {
        id: AFFORDABLE_REWARD_ID,
        name: 'Ice cream',
        price: 30,
        isActive: true,
        groupName: 'Treats',
        icon: '🍦',
        comment: 'A sweet break',
        frequency: { period: 'week', limit: 2 },
        periodProgress: { period: 'week', completed: 0, pending: 0, limit: 2, remaining: 2, available: true, windowStart: '2026-08-24T00:00:00Z', resetAt: '2026-08-31T00:00:00Z' },
    },
    {
        id: UNAFFORDABLE_REWARD_ID,
        name: 'Trip to the cinema',
        price: 120,
        isActive: true,
        groupName: 'Experiences',
        icon: '🎬',
        comment: 'Choose the movie together',
        frequency: { period: 'month', limit: 1 },
        periodProgress: { period: 'month', completed: 0, pending: 0, limit: 1, remaining: 1, available: true, windowStart: '2026-08-01T00:00:00Z', resetAt: '2026-09-01T00:00:00Z' },
    },
    {
        id: INACTIVE_REWARD_ID,
        name: 'Old reward',
        price: 10,
        isActive: false,
        groupName: 'Treats',
    },
];

function clone<T>(value: T): T {
    return structuredClone(value);
}

function createInitialState(): AppState {
    return {
        isAdmin: false,
        role: 'CHILD',
        permission: null,
        balance: initialChild.balance,
        rules: null,
        tasks: [],
        history: [],
        requests: [],
        friends: [],
        childNickname: initialChild.nickname,
        isPinSet: false,
        familyId: 'live-demo-family',
        monthlyLimit: 10000,
        dailyCoinLimit: 0,
        baseData: { tasks: [] },
        catalog: { tasks: [] },
        children: [clone(initialChild)],
        currentChildId: DEMO_CHILD_ID,
        isLoading: false,
    };
}

function snapshot(): { balance: number; requests: Request[] } {
    const state = get(appStore);
    return { balance: state.balance, requests: clone(state.requests) };
}

function rejection(error: string): ApiActionResult {
    return { ok: false, error, errorCode: 'DEMO_REQUEST_REJECTED', status: 400 };
}

function createRequest(input: RewardRequestActionInput, item: ShopItem): Request {
    return {
        id: 'live-demo-request-1',
        requestType: 'shop_purchase',
        itemId: item.id,
        itemName: item.name,
        note: input.note,
        childId: DEMO_CHILD_ID,
        childNickname: initialChild.nickname,
        amount: item.price,
        coins: item.price,
        status: 'pending',
        createdAt: '2026-08-26T12:00:00Z',
    };
}

function createActions(): RewardRequestActions {
    return {
        request: async (input) => {
            const state = get(appStore);
            const item = get(shopItems).find((candidate) => String(candidate.id) === String(input.itemId));
            if (!item) return rejection('Reward is not available.');
            if (item.isActive === false) return rejection('Reward is not available.');
            if (String(input.childId) !== DEMO_CHILD_ID || state.currentChildId !== DEMO_CHILD_ID) return rejection('Child is not available.');
            if (state.balance < item.price) return rejection('Not enough coins.');
            if (state.requests.some((request) => request.status === 'pending' && String(request.itemId) === String(item.id))) {
                return rejection('Reward request is already pending.');
            }

            const request = createRequest(input, item);
            appStore.setState({ requests: [...state.requests, request] });
            return { ok: true, data: snapshot() };
        },
    };
}

export type LiveCoinShopDemoSession = {
    actions: RewardRequestActions;
    initialize: () => void;
    reset: () => void;
    teardown: () => void;
};

export function createLiveCoinShopDemoSession(): LiveCoinShopDemoSession {
    const applyInitialState = () => {
        appStore.set(clone(createInitialState()));
        shopItems.set(clone(initialItems));
    };

    return {
        actions: createActions(),
        initialize: applyInitialState,
        reset: applyInitialState,
        teardown: () => {
            appStore.reset();
            shopItems.set([]);
        },
    };
}

export const liveCoinShopDemoFixture = {
    childId: DEMO_CHILD_ID,
    affordableRewardId: AFFORDABLE_REWARD_ID,
    unaffordableRewardId: UNAFFORDABLE_REWARD_ID,
    inactiveRewardId: INACTIVE_REWARD_ID,
};
