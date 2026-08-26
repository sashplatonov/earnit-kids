import { appStore, type AppState, type HistoryEntry, type Request, type Task } from '$lib/stores/app';
import { catalogRewards } from '$lib/telegram/stores/rewards';
import { shopItems } from '$lib/telegram/stores/shopItems';
import type { ShopItem, CatalogRewardTemplate } from '$lib/telegram/stores/types';
import type { ApiActionResult } from '$lib/services/api';
import type { RewardRequestActionInput, RewardRequestActions } from '$lib/telegram/services/rewardRequestActions';
import type { Locale } from '$lib/i18n';

export const DEMO_FAMILY_ID = 'live-demo-family';
export const DEMO_PARENT_ID = 'live-demo-parent';
export const DEMO_CHILD_ID = 'live-demo-child';
export const DEMO_SECOND_CHILD_ID = 'live-demo-child-2';

const NOW = '2026-08-26T12:00:00Z';
const WEEK_START = '2026-08-24T00:00:00Z';
const WEEK_RESET = '2026-08-31T00:00:00Z';
const MONTH_START = '2026-08-01T00:00:00Z';
const MONTH_RESET = '2026-09-01T00:00:00Z';

export type LiveCoinShopDemoSnapshot = {
    app: AppState;
    shopItems: ShopItem[];
    catalogRewards: CatalogRewardTemplate[];
};

export type LiveCoinShopDemoActionInput = {
    id?: string | number;
    childId?: string | number;
    amount?: number;
    description?: string | null;
    note?: string | null;
    name?: string;
    title?: string;
    price?: number;
    coins?: number;
    groupName?: string | null;
};

export type LiveCoinShopDemoActions = RewardRequestActions & {
    cancelRequest: (requestId: string | number, childId?: string | number) => Promise<ApiActionResult<LiveCoinShopDemoSnapshot>>;
    approveRequest: (requestId: string | number, childId?: string | number) => Promise<ApiActionResult<LiveCoinShopDemoSnapshot>>;
    rejectRequest: (requestId: string | number, childId?: string | number) => Promise<ApiActionResult<LiveCoinShopDemoSnapshot>>;
    awardCoins: (input: LiveCoinShopDemoActionInput) => Promise<ApiActionResult<LiveCoinShopDemoSnapshot>>;
    spendCoins: (input: LiveCoinShopDemoActionInput) => Promise<ApiActionResult<LiveCoinShopDemoSnapshot>>;
    completeTask: (input: LiveCoinShopDemoActionInput) => Promise<ApiActionResult<LiveCoinShopDemoSnapshot>>;
    createTask: (input: LiveCoinShopDemoActionInput) => Promise<ApiActionResult<LiveCoinShopDemoSnapshot>>;
    editTask: (input: LiveCoinShopDemoActionInput) => Promise<ApiActionResult<LiveCoinShopDemoSnapshot>>;
    archiveTask: (input: LiveCoinShopDemoActionInput) => Promise<ApiActionResult<LiveCoinShopDemoSnapshot>>;
    deleteTask: (input: LiveCoinShopDemoActionInput) => Promise<ApiActionResult<LiveCoinShopDemoSnapshot>>;
    createReward: (input: LiveCoinShopDemoActionInput) => Promise<ApiActionResult<LiveCoinShopDemoSnapshot>>;
    editReward: (input: LiveCoinShopDemoActionInput) => Promise<ApiActionResult<LiveCoinShopDemoSnapshot>>;
    archiveReward: (input: LiveCoinShopDemoActionInput) => Promise<ApiActionResult<LiveCoinShopDemoSnapshot>>;
    deleteReward: (input: LiveCoinShopDemoActionInput) => Promise<ApiActionResult<LiveCoinShopDemoSnapshot>>;
    addCatalogReward: (input: LiveCoinShopDemoActionInput) => Promise<ApiActionResult<LiveCoinShopDemoSnapshot>>;
    selectChild: (childId: string | number) => Promise<ApiActionResult<LiveCoinShopDemoSnapshot>>;
    addChild: (input: LiveCoinShopDemoActionInput) => Promise<ApiActionResult<LiveCoinShopDemoSnapshot>>;
    setChildActive: (childId: string | number, active: boolean) => Promise<ApiActionResult<LiveCoinShopDemoSnapshot>>;
    setGroupVisibility: (groupName: string, hidden: boolean) => Promise<ApiActionResult<LiveCoinShopDemoSnapshot>>;
    deleteGroup: (groupName: string, moveTo?: string | null) => Promise<ApiActionResult<LiveCoinShopDemoSnapshot>>;
};

const progress = (period: string, limit: number, completed = 0, pending = 0) => ({
    period, completed, pending, limit, remaining: Math.max(0, limit - completed - pending),
    available: completed + pending < limit,
    windowStart: period === 'week' ? WEEK_START : MONTH_START,
    resetAt: period === 'week' ? WEEK_RESET : MONTH_RESET,
});

const children: AppState['children'] = [
    { id: DEMO_CHILD_ID, nickname: 'Mia', balance: 75, status: 'ACTIVE', ageMin: 8, ageMax: 10, shopGroupOrder: ['Treats', 'Experiences'], taskGroupOrder: ['Home', 'Learning'] },
    { id: DEMO_SECOND_CHILD_ID, nickname: 'Leo', balance: 145, status: 'ACTIVE', ageMin: 6, ageMax: 8, shopGroupOrder: ['Experiences', 'Treats'], taskGroupOrder: ['Learning', 'Home'] },
];

const tasks: Task[] = [
    { id: 'live-demo-task-bed', name: 'Make the bed', coins: 10, isActive: true, groupName: 'Home', icon: '🛏️', frequency: { period: 'day', limit: 1 }, periodProgress: progress('day', 1) },
    { id: 'live-demo-task-read', name: 'Read for 20 minutes', coins: 15, isActive: true, groupName: 'Learning', icon: '📚', frequency: { period: 'week', limit: 5 }, periodProgress: progress('week', 5, 2) },
    { id: 'live-demo-task-archived', name: 'Old chore', coins: 5, isActive: false, groupName: 'Home' },
];

const shop: ShopItem[] = [
    { id: 'live-demo-reward-ice-cream', name: 'Ice cream', price: 30, isActive: true, groupName: 'Treats', icon: '🍦', comment: 'A sweet break', frequency: { period: 'week', limit: 2 }, periodProgress: progress('week', 2) },
    { id: 'live-demo-reward-trip', name: 'Trip to the cinema', price: 120, isActive: true, groupName: 'Experiences', icon: '🎬', comment: 'Choose the movie together', frequency: { period: 'month', limit: 1 }, periodProgress: progress('month', 1) },
    { id: 'live-demo-reward-inactive', name: 'Old reward', price: 10, isActive: false, groupName: 'Treats' },
    { id: 'live-demo-reward-book', name: 'New book', price: 45, isActive: true, groupName: 'Learning', icon: '📖', frequency: { period: 'month', limit: 2 }, periodProgress: progress('month', 2) },
];

const catalogTask = { id: 'live-demo-catalog-task', title: 'Water the plants', comment: 'Keep our home green', coins: 8, groupKey: 'home', groupName: 'Home', active: true, sortOrder: 1 };
const catalogReward: CatalogRewardTemplate = { id: 'live-demo-catalog-reward', title: 'Choose a dessert', comment: 'Pick one together', price: 25, groupKey: 'treats', groupName: 'Treats', active: true, sortOrder: 1 };

function clone<T>(value: T): T { return structuredClone(value); }

function initialSnapshot(locale: Locale = 'en'): LiveCoinShopDemoSnapshot {
    const ru = locale === 'ru';
    const names = ru ? {
        mia: 'Мия', leo: 'Лео', home: 'Дом', learning: 'Учёба', treats: 'Угощения', experiences: 'Впечатления',
        bed: 'Заправить кровать', read: 'Читать 20 минут', oldTask: 'Старая задача', iceCream: 'Мороженое',
        cinema: 'Поход в кино', oldReward: 'Старая награда', book: 'Новая книга', water: 'Полить растения',
        dessert: 'Выбрать десерт', school: 'Для школы', bonus: 'Еженедельный бонус', sweetBreak: 'Сладкий перерыв', movie: 'Выберите фильм вместе'
    } : {
        mia: 'Mia', leo: 'Leo', home: 'Home', learning: 'Learning', treats: 'Treats', experiences: 'Experiences',
        bed: 'Make the bed', read: 'Read for 20 minutes', oldTask: 'Old chore', iceCream: 'Ice cream',
        cinema: 'Trip to the cinema', oldReward: 'Old reward', book: 'New book', water: 'Water the plants',
        dessert: 'Choose a dessert', school: 'For school', bonus: 'Weekly bonus', sweetBreak: 'A sweet break', movie: 'Choose the movie together'
    };
    const localizedChildren = children.map((entry, index) => ({ ...entry, nickname: index === 0 ? names.mia : names.leo, shopGroupOrder: [names.treats, names.experiences], taskGroupOrder: [names.home, names.learning] }));
    const localizedTasks = tasks.map((entry) => ({ ...entry, name: String(entry.id).endsWith('bed') ? names.bed : String(entry.id).endsWith('read') ? names.read : names.oldTask, groupName: entry.groupName === 'Home' ? names.home : names.learning }));
    const localizedShop = shop.map((entry) => ({ ...entry, name: String(entry.id).endsWith('ice-cream') ? names.iceCream : String(entry.id).endsWith('trip') ? names.cinema : String(entry.id).endsWith('inactive') ? names.oldReward : names.book, groupName: entry.groupName === 'Treats' ? names.treats : entry.groupName === 'Experiences' ? names.experiences : names.learning, comment: String(entry.id).endsWith('ice-cream') ? names.sweetBreak : entry.comment }));
    const localizedCatalogTask = { ...catalogTask, title: names.water, comment: ru ? 'Сохраним дом зелёным' : 'Keep our home green', groupName: names.home };
    const localizedCatalogReward = { ...catalogReward, title: names.dessert, comment: names.movie, groupName: names.treats };
    const requests: Request[] = [
        { id: 'live-demo-request-pending', requestType: 'shop_purchase', itemId: 'live-demo-reward-book', itemName: names.book, childId: DEMO_SECOND_CHILD_ID, childNickname: names.leo, amount: 45, coins: 45, note: names.school, status: 'pending', createdAt: '2026-08-26T10:00:00Z' },
        { id: 'live-demo-request-approved', requestType: 'shop_purchase', itemId: 'live-demo-reward-ice-cream', itemName: names.iceCream, childId: DEMO_SECOND_CHILD_ID, childNickname: names.leo, amount: 30, coins: 30, status: 'approved', createdAt: '2026-08-25T10:00:00Z' },
        { id: 'live-demo-request-rejected', requestType: 'task_completion', taskId: 'live-demo-task-read', taskName: names.read, childId: DEMO_CHILD_ID, childNickname: names.mia, amount: 15, coins: 15, status: 'rejected', createdAt: '2026-08-24T10:00:00Z' },
    ];
    const history: HistoryEntry[] = [
        { id: 'live-demo-history-1', type: 'earn', amount: 15, title: names.read, taskId: 'live-demo-task-read', groupName: names.learning, childId: DEMO_CHILD_ID, createdAt: '2026-08-25T18:00:00Z' },
        { id: 'live-demo-history-2', type: 'purchase', amount: -30, title: names.iceCream, itemId: 'live-demo-reward-ice-cream', groupName: names.treats, childId: DEMO_SECOND_CHILD_ID, createdAt: '2026-08-25T12:00:00Z' },
        { id: 'live-demo-history-3', type: 'admin', amount: 50, description: names.bonus, childId: DEMO_SECOND_CHILD_ID, createdAt: '2026-08-24T09:00:00Z' },
    ];
    return {
        app: { isAdmin: true, role: 'PARENT', permission: 'family_admin', balance: localizedChildren[0].balance, rules: null, tasks: clone(localizedTasks), history, requests, friends: [], childNickname: names.mia, isPinSet: false, familyId: DEMO_FAMILY_ID, monthlyLimit: 10000, dailyCoinLimit: 100, baseData: { tasks: clone(localizedTasks) }, catalog: { tasks: [localizedCatalogTask] }, children: clone(localizedChildren), currentChildId: DEMO_CHILD_ID, isLoading: false },
        shopItems: clone(localizedShop),
        catalogRewards: [clone(localizedCatalogReward)],
    };
}

function rejection(error: string, errorCode = 'DEMO_REQUEST_REJECTED'): ApiActionResult<never> { return { ok: false, error, errorCode, status: 400 }; }

export function createLiveCoinShopDemoActions(read: () => LiveCoinShopDemoSnapshot, commit: (next: LiveCoinShopDemoSnapshot) => LiveCoinShopDemoSnapshot): LiveCoinShopDemoActions {
    const mutate = (fn: (state: LiveCoinShopDemoSnapshot) => LiveCoinShopDemoSnapshot): ApiActionResult<LiveCoinShopDemoSnapshot> => ({ ok: true, data: commit(fn(read())) });
    const child = (state: LiveCoinShopDemoSnapshot, id = state.app.currentChildId) => state.app.children.find((entry) => String(entry.id) === String(id));
    const item = (state: LiveCoinShopDemoSnapshot, id: unknown) => state.shopItems.find((entry) => String(entry.id) === String(id));
    const request = async (input: RewardRequestActionInput) => {
        const state = read(); const current = child(state, input.childId); const reward = item(state, input.itemId);
        if (!current || String(state.app.currentChildId) !== String(input.childId)) return rejection('Child is not available.');
        const task = state.app.tasks.find((entry) => String(entry.id) === String(input.itemId));
        if (task) {
            if (task.isActive === false || task.periodProgress?.available === false) return rejection('Task is not available.');
            if (state.app.requests.some((entry) => entry.status === 'pending' && String(entry.taskId) === String(task.id) && String(entry.childId) === String(current.id))) return rejection('Task request is already pending.');
            return mutate((next) => ({ ...next, app: { ...next.app, requests: [...next.app.requests, { id: `live-demo-request-generated-${next.app.requests.length + 1}`, requestType: 'task_completion', taskId: task.id, taskName: task.name, childId: current.id, childNickname: current.nickname, amount: task.coins, coins: task.coins, note: input.note, status: 'pending', createdAt: NOW }] } }));
        }
        if (!reward || reward.isActive === false) return rejection('Reward is not available.');
        if (current.balance < reward.price) return rejection('Not enough coins.');
        if ((reward.periodProgress?.available === false) || state.app.requests.some((entry) => entry.status === 'pending' && String(entry.itemId) === String(reward.id) && String(entry.childId) === String(current.id))) return rejection('Reward request is already pending.');
        return mutate((next) => ({ ...next, app: { ...next.app, requests: [...next.app.requests, { id: 'live-demo-request-generated-1', requestType: 'shop_purchase', itemId: reward.id, itemName: reward.name, childId: current.id, childNickname: current.nickname, amount: reward.price, coins: reward.price, note: input.note, status: 'pending', createdAt: NOW }] } }));
    };
    const decide = (requestId: string | number, status: 'approved' | 'rejected') => mutate((state) => {
        const requestEntry = state.app.requests.find((entry) => String(entry.id) === String(requestId));
        if (!requestEntry || requestEntry.status !== 'pending') return state;
        if (status === 'approved' && requestEntry.taskId) {
            const task = state.app.tasks.find((entry) => String(entry.id) === String(requestEntry.taskId));
            const target = child(state, requestEntry.childId);
            if (!task || !target) return state;
            const balance = target.balance + (task.coins ?? requestEntry.coins ?? 0);
            return { ...state, app: { ...state.app, children: state.app.children.map((entry) => entry.id === target.id ? { ...entry, balance } : entry), balance: String(state.app.currentChildId) === String(target.id) ? balance : state.app.balance, requests: state.app.requests.map((entry) => entry.id === requestEntry.id ? { ...entry, status } : entry), history: [{ id: `live-demo-history-${state.app.history.length + 1}`, type: 'task_completed', amount: task.coins, title: task.name, taskId: task.id, groupName: task.groupName, childId: target.id, createdAt: NOW }, ...state.app.history] } };
        }
        if (status === 'approved' && requestEntry.itemId) {
            const target = child(state, requestEntry.childId); const reward = item(state, requestEntry.itemId);
            if (!target || !reward || target.balance < reward.price) return state;
            const nextChildren = state.app.children.map((entry) => String(entry.id) === String(target.id) ? { ...entry, balance: entry.balance - reward.price } : entry);
            return { ...state, app: { ...state.app, children: nextChildren, balance: String(state.app.currentChildId) === String(target.id) ? state.app.balance - reward.price : state.app.balance, requests: state.app.requests.map((entry) => entry.id === requestEntry.id ? { ...entry, status } : entry), history: [{ id: `live-demo-history-${state.app.history.length + 1}`, type: 'purchase', amount: -reward.price, title: reward.name, itemName: reward.name, itemId: reward.id, groupName: reward.groupName, childId: target.id, createdAt: NOW }, ...state.app.history] }, shopItems: state.shopItems.map((entry) => entry.id === reward.id ? { ...entry, lastPurchasedAt: NOW, periodProgress: entry.periodProgress ? { ...entry.periodProgress, completed: entry.periodProgress.completed + 1, remaining: Math.max(0, entry.periodProgress.remaining - 1), available: entry.periodProgress.remaining > 1 } : entry } : entry) as ShopItem[] };
        }
        return { ...state, app: { ...state.app, requests: state.app.requests.map((entry) => entry.id === requestEntry.id ? { ...entry, status } : entry) } };
    });
    const selectChild = async (childId: string | number) => child(read(), childId) ? mutate((state) => { const selected = child(state, childId)!; return { ...state, app: { ...state.app, currentChildId: selected.id, childNickname: selected.nickname, balance: selected.balance } }; }) : rejection('Child is not available.');
    const setChildActive = async (childId: string | number, active: boolean) => {
        const state = read();
        const target = child(state, childId);
        if (!target) return rejection('Child is not available.');
        return mutate((next) => {
            const children = next.app.children.map((entry) => entry.id === target.id ? { ...entry, status: active ? 'ACTIVE' as const : 'INACTIVE' as const } : entry);
            const selected = active || String(next.app.currentChildId) !== String(target.id)
                ? next.app.children.find((entry) => String(entry.id) === String(next.app.currentChildId))
                : children.find((entry) => entry.status === 'ACTIVE');
            return { ...next, app: { ...next.app, children, currentChildId: selected?.id ?? null, childNickname: selected?.nickname ?? '', balance: selected?.balance ?? 0 } };
        });
    };
    const coinMutation = (input: LiveCoinShopDemoActionInput, sign: 1 | -1) => { const state = read(); const target = child(state, input.childId); const amount = Number(input.amount ?? 0); if (!target || !Number.isFinite(amount) || amount <= 0 || (sign < 0 && target.balance < amount)) return rejection(sign < 0 ? 'Not enough coins.' : 'Coin amount is invalid.'); return mutate((next) => { const balance = target.balance + sign * amount; return { ...next, app: { ...next.app, children: next.app.children.map((entry) => entry.id === target.id ? { ...entry, balance } : entry), balance: next.app.currentChildId === target.id ? balance : next.app.balance, history: [{ id: `live-demo-history-${next.app.history.length + 1}`, type: sign > 0 ? 'admin' : 'spend', amount: sign * amount, description: input.description ?? null, childId: target.id, createdAt: NOW }, ...next.app.history] } }; }); };
    const taskMutation = (input: LiveCoinShopDemoActionInput, mode: 'create' | 'edit' | 'archive' | 'delete' | 'complete') => { const state = read(); const existing = state.app.tasks.find((entry) => String(entry.id) === String(input.id)); if (mode !== 'create' && !existing) return rejection('Task is not available.'); if (mode === 'complete' && existing?.periodProgress?.available === false) return rejection('Task limit reached.'); if (mode === 'complete' && !child(state)) return rejection('Child is not available.'); return mutate((next) => { if (mode === 'create') { const created: Task = { id: `live-demo-task-${next.app.tasks.length + 1}`, name: input.name ?? input.title ?? 'New task', coins: input.coins ?? 10, groupName: input.groupName ?? 'Home', isActive: true }; return { ...next, app: { ...next.app, tasks: [...next.app.tasks, created], baseData: { tasks: [...next.app.baseData.tasks, created] } } }; } if (mode === 'delete') return { ...next, app: { ...next.app, tasks: next.app.tasks.filter((entry) => entry.id !== existing!.id) } }; const target = next.app.children.find((entry) => String(entry.id) === String(next.app.currentChildId)); const balance = target ? target.balance + (existing!.coins ?? 0) : next.app.balance; const children = mode === 'complete' && target ? next.app.children.map((entry) => entry.id === target.id ? { ...entry, balance } : entry) : next.app.children; const tasks = next.app.tasks.map((entry) => entry.id === existing!.id ? { ...entry, ...(mode === 'edit' ? { name: input.name ?? input.title ?? entry.name, coins: input.coins ?? entry.coins, groupName: input.groupName ?? entry.groupName } : {}), ...(mode === 'archive' ? { isActive: false } : {}), ...(mode === 'complete' ? { lastCompletedAt: NOW } : {}) } : entry); return { ...next, app: { ...next.app, tasks, children, balance: mode === 'complete' ? balance : next.app.balance, history: mode === 'complete' ? [{ id: `live-demo-history-${next.app.history.length + 1}`, type: 'task_completed', amount: existing!.coins, title: existing!.name, taskId: existing!.id, groupName: existing!.groupName, childId: target?.id, createdAt: NOW }, ...next.app.history] : next.app.history } }; }); };
    const rewardMutation = (input: LiveCoinShopDemoActionInput, mode: 'create' | 'edit' | 'archive' | 'delete') => mutate((state) => { const existing = item(state, input.id); if (mode !== 'create' && !existing) return state; if (mode === 'create') return { ...state, shopItems: [...state.shopItems, { id: `live-demo-reward-${state.shopItems.length + 1}`, name: input.name ?? input.title ?? 'New reward', price: input.price ?? 25, groupName: input.groupName ?? 'Treats', isActive: true }] }; const next = state.shopItems.map((entry) => entry.id === existing!.id ? { ...entry, ...(mode === 'edit' ? { name: input.name ?? input.title ?? entry.name, price: input.price ?? entry.price, groupName: input.groupName ?? entry.groupName } : {}), ...(mode === 'archive' ? { isActive: false } : {}) } : entry); return { ...state, shopItems: mode === 'delete' ? state.shopItems.filter((entry) => entry.id !== existing!.id) : next }; });
    const purchaseReward = async (input: LiveCoinShopDemoActionInput) => { const state = read(); const reward = item(state, input.id); const target = child(state, input.childId); if (!reward || reward.isActive === false) return rejection('Reward is not available.'); if (!target || target.balance < reward.price) return rejection('Not enough coins.'); if (reward.periodProgress?.available === false) return rejection('Reward limit reached.'); return mutate((next) => ({ ...next, app: { ...next.app, children: next.app.children.map((entry) => entry.id === target.id ? { ...entry, balance: entry.balance - reward.price } : entry), balance: next.app.currentChildId === target.id ? target.balance - reward.price : next.app.balance, history: [{ id: `live-demo-history-${next.app.history.length + 1}`, type: 'purchase', amount: -reward.price, title: reward.name, itemId: reward.id, groupName: reward.groupName, childId: target.id, createdAt: NOW }, ...next.app.history] }, shopItems: next.shopItems.map((entry) => entry.id === reward.id ? { ...entry, lastPurchasedAt: NOW } : entry) })); };
    return { request, cancelRequest: async (id) => mutate((state) => ({ ...state, app: { ...state.app, requests: state.app.requests.filter((entry) => !(String(entry.id) === String(id) && entry.status === 'pending')) } })), approveRequest: async (id) => decide(id, 'approved'), rejectRequest: async (id) => decide(id, 'rejected'), awardCoins: async (input) => coinMutation(input, 1), spendCoins: purchaseReward, completeTask: async (input) => taskMutation(input, 'complete'), createTask: async (input) => taskMutation(input, 'create'), editTask: async (input) => taskMutation(input, 'edit'), archiveTask: async (input) => taskMutation(input, 'archive'), deleteTask: async (input) => taskMutation(input, 'delete'), createReward: async (input) => rewardMutation(input, 'create'), editReward: async (input) => rewardMutation(input, 'edit'), archiveReward: async (input) => rewardMutation(input, 'archive'), deleteReward: async (input) => rewardMutation(input, 'delete'), addCatalogReward: async (input) => { const template = read().catalogRewards.find((entry) => String(entry.id) === String(input.id)); return template ? mutate((state) => ({ ...state, shopItems: [...state.shopItems, { id: `live-demo-reward-${state.shopItems.length + 1}`, name: template.title, price: template.price, groupName: template.groupName, isActive: true }] })) : rejection('Catalog reward is not available.'); }, selectChild, setChildActive, addChild: async (input) => mutate((state) => { const next = { id: `live-demo-child-${state.app.children.length + 1}`, nickname: input.name ?? 'New child', balance: 0, status: 'ACTIVE' as const }; return { ...state, app: { ...state.app, children: [...state.app.children, next] } }; }), setGroupVisibility: async (groupName, hidden) => mutate((state) => ({ ...state, app: { ...state.app, children: state.app.children.map((entry) => ({ ...entry, hiddenShopGroupOrder: hidden ? [...(entry.hiddenShopGroupOrder ?? []), groupName] : (entry.hiddenShopGroupOrder ?? []).filter((name) => name !== groupName) })) } })), deleteGroup: async (groupName, moveTo) => mutate((state) => ({ ...state, shopItems: state.shopItems.map((entry) => entry.groupName === groupName ? { ...entry, groupName: moveTo } : entry) })), };
}

export type LiveCoinShopDemoSession = { actions: LiveCoinShopDemoActions; initialize: () => void; reset: () => void; teardown: () => void; snapshot: () => LiveCoinShopDemoSnapshot; };

export function createLiveCoinShopDemoSession(locale: Locale = 'en'): LiveCoinShopDemoSession {
    let canonical = initialSnapshot(locale);
    const publish = (next: LiveCoinShopDemoSnapshot) => { canonical = clone(next); appStore.set(clone(canonical.app)); shopItems.set(clone(canonical.shopItems)); catalogRewards.set(clone(canonical.catalogRewards)); return clone(canonical); };
    const session = { actions: null as unknown as LiveCoinShopDemoActions, initialize: () => publish(initialSnapshot(locale)), reset: () => publish(initialSnapshot(locale)), teardown: () => { appStore.reset(); shopItems.set([]); catalogRewards.set([]); }, snapshot: () => clone(canonical) };
    session.actions = createLiveCoinShopDemoActions(() => clone(canonical), publish);
    return session;
}

export const liveCoinShopDemoFixture = { familyId: DEMO_FAMILY_ID, childId: DEMO_CHILD_ID, secondChildId: DEMO_SECOND_CHILD_ID, affordableRewardId: 'live-demo-reward-ice-cream', unaffordableRewardId: 'live-demo-reward-trip', inactiveRewardId: 'live-demo-reward-inactive', pendingRequestId: 'live-demo-request-pending', approvedRequestId: 'live-demo-request-approved', rejectedRequestId: 'live-demo-request-rejected' };
