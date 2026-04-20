import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

function createLocalStorageStub(seed: Record<string, string> = {}) {
    const state = new Map(Object.entries(seed));

    return {
        state,
        localStorage: {
            getItem: vi.fn((key: string) => state.get(key) ?? null),
            setItem: vi.fn((key: string, value: string) => {
                state.set(key, String(value));
            }),
            removeItem: vi.fn((key: string) => {
                state.delete(key);
            }),
            clear: vi.fn(() => {
                state.clear();
            }),
        },
    };
}

describe('tabStore persistence', () => {
    beforeEach(() => {
        vi.resetModules();
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('restores the last admin tab on init', async () => {
        const storage = createLocalStorageStub({ 'earnit-last-admin-tab': 'history' });
        vi.stubGlobal('localStorage', storage.localStorage);

        const { tabStore } = await import('../../src/lib/stores/tabs');
        let activeTab = '';
        const unsubscribe = tabStore.subscribe(value => {
            activeTab = value;
        });

        tabStore.initForRole(true);

        expect(activeTab).toBe('history');
        unsubscribe();
    });

    it('persists the last child tab after switching', async () => {
        const storage = createLocalStorageStub();
        vi.stubGlobal('localStorage', storage.localStorage);

        const { tabStore } = await import('../../src/lib/stores/tabs');
        let activeTab = '';
        const unsubscribe = tabStore.subscribe(value => {
            activeTab = value;
        });

        tabStore.initForRole(false);
        tabStore.setTab('requests');

        expect(activeTab).toBe('requests');
        expect(storage.state.get('earnit-last-child-tab')).toBe('requests');
        unsubscribe();
    });

    it('falls back to the child default for admin-only stored tabs', async () => {
        const storage = createLocalStorageStub({ 'earnit-last-child-tab': 'limits' });
        vi.stubGlobal('localStorage', storage.localStorage);

        const { tabStore } = await import('../../src/lib/stores/tabs');
        let activeTab = '';
        const unsubscribe = tabStore.subscribe(value => {
            activeTab = value;
        });

        tabStore.initForRole(false);

        expect(activeTab).toBe('tasks');
        unsubscribe();
    });
});