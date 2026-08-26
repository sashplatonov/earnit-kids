import type { CatalogRewardTemplate, ShopItem } from '$lib/telegram/stores/types';
import type { AppState, HistoryEntry, Request, Task } from '$lib/stores/app';
import { DEMO_FAMILY_ID, DEMO_CHILD_ID, DEMO_SECOND_CHILD_ID, type LiveCoinShopDemoSnapshot } from './liveCoinShopDemoSession';

export type { LiveCoinShopDemoSnapshot };

export const liveCoinShopDemoFixture = {
    familyId: DEMO_FAMILY_ID,
    children: [DEMO_CHILD_ID, DEMO_SECOND_CHILD_ID],
    groups: { tasks: ['Home', 'Learning'], rewards: ['Treats', 'Experiences', 'Learning'] },
    permissions: 'family_admin' as const,
};

export function createLiveCoinShopDemoFixture(): LiveCoinShopDemoSnapshot {
    const child = (id: string, nickname: string, balance: number): AppState['children'][number] => ({ id, nickname, balance, status: 'ACTIVE', shopGroupOrder: ['Treats', 'Experiences'], taskGroupOrder: ['Home', 'Learning'] });
    const tasks: Task[] = [{ id: 'fixture-task-bed', name: 'Make the bed', coins: 10, groupName: 'Home', isActive: true }];
    const rewards: ShopItem[] = [{ id: 'fixture-reward-ice-cream', name: 'Ice cream', price: 30, groupName: 'Treats', isActive: true }];
    const requests: Request[] = [];
    const history: HistoryEntry[] = [];
    const catalogRewards: CatalogRewardTemplate[] = [{ id: 'fixture-catalog-reward', title: 'Choose a dessert', price: 25, groupKey: 'treats', groupName: 'Treats', active: true }];
    return { app: { isAdmin: true, role: 'PARENT', permission: 'family_admin', balance: 75, rules: null, tasks, history, requests, friends: [], childNickname: 'Mia', isPinSet: false, familyId: DEMO_FAMILY_ID, monthlyLimit: 10000, dailyCoinLimit: 100, baseData: { tasks }, catalog: { tasks: [] }, children: [child(DEMO_CHILD_ID, 'Mia', 75), child(DEMO_SECOND_CHILD_ID, 'Leo', 145)], currentChildId: DEMO_CHILD_ID, isLoading: false }, shopItems: rewards, catalogRewards };
}
