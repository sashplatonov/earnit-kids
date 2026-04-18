/** Tab store — replaces legacy main-tabs.js internal tab activation */
import { writable } from 'svelte/store';

export type TabName =
    | 'analytics'
    | 'tasks'
    | 'shop'
    | 'requests'
    | 'history'
    | 'friends'
    | 'rules'
    | 'settings'
    | 'limits'
    | 'catalog';

const CHILD_DEFAULT_TAB: TabName = 'tasks';
const ADMIN_DEFAULT_TAB: TabName = 'analytics';

function createTabStore() {
    const { subscribe, set } = writable<TabName>(CHILD_DEFAULT_TAB);
    return {
        subscribe,
        setTab(tab: TabName) {
            set(tab);
        },
        initForRole(isAdmin: boolean) {
            set(isAdmin ? ADMIN_DEFAULT_TAB : CHILD_DEFAULT_TAB);
        },
    };
}

export const tabStore = createTabStore();
