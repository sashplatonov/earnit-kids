import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../src/lib/stores/toasts', () => ({
    showToast: vi.fn(),
}));

import { initializePwa } from '../../src/lib/services/pwa';

type Listener = (event: Event) => unknown;

class FakeEventTarget {
    private listeners = new Map<string, Set<Listener>>();

    addEventListener(type: string, listener: Listener) {
        const listeners = this.listeners.get(type) ?? new Set<Listener>();
        listeners.add(listener);
        this.listeners.set(type, listeners);
    }

    removeEventListener(type: string, listener: Listener) {
        this.listeners.get(type)?.delete(listener);
    }

    async dispatch(type: string) {
        const listeners = Array.from(this.listeners.get(type) ?? []);
        await Promise.all(listeners.map((listener) => listener(new Event(type))));
    }
}

function setupBrowserGlobals(registration: ServiceWorkerRegistration) {
    const windowTarget = new FakeEventTarget();
    const documentTarget = new FakeEventTarget();
    const intervalIds = new Set<number>();
    let nextIntervalId = 1;

    vi.stubGlobal('HTMLElement', class {});
    vi.stubGlobal('window', {
        location: {
            hostname: 'earnit-kids.igo.mywire.org',
            reload: vi.fn(),
        },
        matchMedia: vi.fn(() => ({ matches: false })),
        addEventListener: windowTarget.addEventListener.bind(windowTarget),
        removeEventListener: windowTarget.removeEventListener.bind(windowTarget),
        setInterval: vi.fn(() => {
            const id = nextIntervalId;
            nextIntervalId += 1;
            intervalIds.add(id);
            return id;
        }),
        clearInterval: vi.fn((id: number) => intervalIds.delete(id)),
    });

    vi.stubGlobal('document', {
        visibilityState: 'visible',
        querySelector: vi.fn(() => null),
        getElementById: vi.fn(() => null),
        addEventListener: documentTarget.addEventListener.bind(documentTarget),
        removeEventListener: documentTarget.removeEventListener.bind(documentTarget),
    });

    vi.stubGlobal('navigator', {
        userAgent: '',
        maxTouchPoints: 0,
        onLine: true,
        serviceWorker: {
            controller: {},
            register: vi.fn().mockResolvedValue(registration),
            addEventListener: vi.fn(),
            removeEventListener: vi.fn(),
        },
    });

    return {
        dispatchWindow: windowTarget.dispatch.bind(windowTarget),
        intervalIds,
    };
}

function createRegistration(update: () => Promise<void>): ServiceWorkerRegistration {
    const target = new FakeEventTarget();
    const registration = {
        active: {} as ServiceWorker,
        waiting: null,
        installing: null,
        update,
        addEventListener: target.addEventListener.bind(target),
        removeEventListener: target.removeEventListener.bind(target),
    };

    return registration as unknown as ServiceWorkerRegistration;
}

describe('initializePwa service worker updates', () => {
    afterEach(() => {
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
    });

    it('does not log stale registration DOMException from update checks', async () => {
        const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => undefined);
        const update = vi.fn().mockRejectedValue(
            new DOMException(
                'An attempt was made to use an object that is not, or is no longer, usable',
                'InvalidStateError'
            )
        );
        const registration = createRegistration(update);
        const browser = setupBrowserGlobals(registration);

        const cleanup = await initializePwa(() => undefined);
        await browser.dispatchWindow('focus');

        expect(update).toHaveBeenCalledTimes(1);
        expect(consoleSpy).not.toHaveBeenCalledWith('SW update check failed:', expect.any(DOMException));

        cleanup();
    });

    it('removes service worker update checks during cleanup', async () => {
        const update = vi.fn().mockResolvedValue(undefined);
        const registration = createRegistration(update);
        const browser = setupBrowserGlobals(registration);

        const cleanup = await initializePwa(() => undefined);
        cleanup();
        await browser.dispatchWindow('focus');

        expect(update).not.toHaveBeenCalled();
        expect(browser.intervalIds.size).toBe(0);
    });
});
