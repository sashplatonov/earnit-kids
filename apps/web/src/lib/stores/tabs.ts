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
const CHILD_STORAGE_KEY = 'earnit-last-child-tab';
const ADMIN_STORAGE_KEY = 'earnit-last-admin-tab';

const CHILD_TABS = new Set<TabName>([
    'analytics',
    'tasks',
    'shop',
    'requests',
    'history',
    'friends',
    'rules',
    'settings',
]);

const ADMIN_TABS = new Set<TabName>([
    'analytics',
    'tasks',
    'shop',
    'requests',
    'history',
    'friends',
    'rules',
    'settings',
    'limits',
    'catalog',
]);

function isTabName(value: string | null): value is TabName {
    return value != null && (CHILD_TABS.has(value as TabName) || ADMIN_TABS.has(value as TabName));
}

function allowedTabs(isAdmin: boolean): Set<TabName> {
    return isAdmin ? ADMIN_TABS : CHILD_TABS;
}

function storageKey(isAdmin: boolean): string {
    return isAdmin ? ADMIN_STORAGE_KEY : CHILD_STORAGE_KEY;
}

function readStoredTab(isAdmin: boolean): TabName | null {
    try {
        const value = typeof localStorage !== 'undefined' ? localStorage.getItem(storageKey(isAdmin)) : null;
        return isTabName(value) && allowedTabs(isAdmin).has(value) ? value : null;
    } catch {
        return null;
    }
}

function persistTab(isAdmin: boolean, tab: TabName): void {
    try {
        if (typeof localStorage !== 'undefined') {
            localStorage.setItem(storageKey(isAdmin), tab);
        }
    } catch {
        // Ignore storage failures.
    }
}

function createTabStore() {
    const { subscribe, set } = writable<TabName>(CHILD_DEFAULT_TAB);
    let currentRoleIsAdmin = false;

    return {
        subscribe,
        setTab(tab: TabName) {
            if (!allowedTabs(currentRoleIsAdmin).has(tab)) {
                return;
            }
            persistTab(currentRoleIsAdmin, tab);
            set(tab);
        },
        initForRole(isAdmin: boolean) {
            currentRoleIsAdmin = isAdmin;
            set(readStoredTab(isAdmin) ?? (isAdmin ? ADMIN_DEFAULT_TAB : CHILD_DEFAULT_TAB));
        },
    };
}

export const tabStore = createTabStore();
