import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../src/lib/stores/toasts', () => ({
    showToast: vi.fn(),
}));

import { showToast } from '../../src/lib/stores/toasts';
import { initializePwa } from '../../src/lib/services/pwa';

type Listener = (event: Event) => unknown;
type RegistrationWithDispatch = ServiceWorkerRegistration & {
    dispatch: (type: string, event?: Event) => Promise<void>;
};
type NavigatorStub = Navigator & {
    onLine: boolean;
    serviceWorker?: ServiceWorkerContainer & {
        controller: ServiceWorker | null;
        register: ReturnType<typeof vi.fn>;
        addEventListener: ReturnType<typeof vi.fn>;
        removeEventListener: ReturnType<typeof vi.fn>;
    };
};
type WindowStub = Window & {
    location: {
        hostname: string;
        reload: ReturnType<typeof vi.fn>;
    };
    navigator: {
        standalone: boolean;
    };
    caches?: {
        keys: ReturnType<typeof vi.fn>;
        delete: ReturnType<typeof vi.fn>;
    };
};
type DocumentStub = Document & {
    visibilityState: DocumentVisibilityState;
    documentElement: {
        scrollTop: number;
    };
};

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

    async dispatch(type: string, event: Event = new Event(type)) {
        const listeners = Array.from(this.listeners.get(type) ?? []);
        await Promise.all(listeners.map((listener) => listener(event)));
    }
}

class FakeElement {
    classList = {
        classes: new Set<string>(),
        add: (...names: string[]) => {
            names.forEach((name) => this.classList.classes.add(name));
        },
        remove: (...names: string[]) => {
            names.forEach((name) => this.classList.classes.delete(name));
        },
        toggle: (name: string, force?: boolean) => {
            if (force === undefined ? !this.classList.classes.has(name) : force) {
                this.classList.classes.add(name);
                return true;
            }
            this.classList.classes.delete(name);
            return false;
        },
    };

    style: Record<string, string> = {};

    textContent = '';

    private listeners = new Map<string, Set<Listener>>();

    addEventListener(type: string, listener: Listener) {
        const listeners = this.listeners.get(type) ?? new Set<Listener>();
        listeners.add(listener);
        this.listeners.set(type, listeners);
    }

    removeEventListener(type: string, listener: Listener) {
        this.listeners.get(type)?.delete(listener);
    }

    async dispatch(type: string, event: Event = new Event(type)) {
        const listeners = Array.from(this.listeners.get(type) ?? []);
        await Promise.all(listeners.map((listener) => listener(event)));
    }
}

function createRegistration(
    update: () => Promise<void>,
    options: {
        waiting?: { postMessage: (message: Record<string, unknown>) => void } | null;
        installing?: FakeEventTarget & { state: string };
    } = {},
) : RegistrationWithDispatch {
    const target = new FakeEventTarget();
    const registration = {
        active: {} as ServiceWorker,
        waiting: options.waiting ?? null,
        installing: options.installing ?? null,
        update,
        addEventListener: target.addEventListener.bind(target),
        removeEventListener: target.removeEventListener.bind(target),
        dispatch: target.dispatch.bind(target),
    };

    return registration as unknown as RegistrationWithDispatch;
}

type SetupOptions = {
    hostname?: string;
    userAgent?: string;
    maxTouchPoints?: number;
    online?: boolean;
    buildVersion?: string;
    mobileMatches?: boolean;
    standaloneMatches?: boolean;
    serviceWorker?: boolean;
    caches?: {
        keys: ReturnType<typeof vi.fn>;
        delete: ReturnType<typeof vi.fn>;
    };
    elements?: Record<string, FakeElement | null>;
};

function setupBrowserGlobals(registration: ServiceWorkerRegistration, options: SetupOptions = {}) {
    const windowTarget = new FakeEventTarget();
    const documentTarget = new FakeEventTarget();
    const intervalIds = new Set<number>();
    let nextIntervalId = 1;
    const elements = options.elements ?? {};
    const reload = vi.fn();
    const windowStub = {
        location: {
            hostname: options.hostname ?? 'earnit-kids.igo.mywire.org',
            reload,
        },
        navigator: {
            standalone: Boolean(options.standaloneMatches),
        },
        matchMedia: vi.fn((query: string) => ({
            matches: query === '(display-mode: standalone)' ? Boolean(options.standaloneMatches) : Boolean(options.mobileMatches),
        })),
        addEventListener: windowTarget.addEventListener.bind(windowTarget),
        removeEventListener: windowTarget.removeEventListener.bind(windowTarget),
        setInterval: vi.fn(() => {
            const id = nextIntervalId;
            nextIntervalId += 1;
            intervalIds.add(id);
            return id;
        }),
        clearInterval: vi.fn((id: number) => intervalIds.delete(id)),
        caches: options.caches,
    } as unknown as WindowStub;

    vi.stubGlobal('HTMLElement', FakeElement);
    vi.stubGlobal('window', windowStub);

    if (options.caches) {
        vi.stubGlobal('caches', options.caches);
    }

    const documentStub = {
        visibilityState: 'visible',
        documentElement: {
            scrollTop: 0,
        },
        querySelector: vi.fn((selector: string) => {
            if (selector === 'meta[name="app-build-version"]' && options.buildVersion) {
                return {
                    getAttribute: (name: string) => (name === 'content' ? options.buildVersion : null),
                };
            }

            return null;
        }),
        getElementById: vi.fn((id: string) => elements[id] ?? null),
        addEventListener: documentTarget.addEventListener.bind(documentTarget),
        removeEventListener: documentTarget.removeEventListener.bind(documentTarget),
    } as unknown as DocumentStub;
    vi.stubGlobal('document', documentStub);

    const navigatorStub: NavigatorStub = {
        userAgent: options.userAgent ?? '',
        maxTouchPoints: options.maxTouchPoints ?? 0,
        onLine: options.online ?? true,
    } as NavigatorStub;

    if (options.serviceWorker !== false) {
        navigatorStub.serviceWorker = {
            controller: {} as ServiceWorker,
            register: vi.fn().mockResolvedValue(registration),
            addEventListener: vi.fn(),
            removeEventListener: vi.fn(),
        } as unknown as NavigatorStub['serviceWorker'];
    }

    vi.stubGlobal('navigator', navigatorStub);

    return {
        dispatchWindow: windowTarget.dispatch.bind(windowTarget),
        dispatchDocument: documentTarget.dispatch.bind(documentTarget),
        intervalIds,
        windowStub,
        documentStub,
        navigatorStub,
    };
}

describe('initializePwa service worker updates', () => {
    afterEach(() => {
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
        vi.mocked(showToast).mockClear();
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

    it('hides install and offline controls on desktop browsers', async () => {
        const registration = createRegistration(vi.fn().mockResolvedValue(undefined));
        const button = new FakeElement();
        const iosHint = new FakeElement();
        const banner = new FakeElement();
        setupBrowserGlobals(registration, {
            elements: {
                'pwa-install-btn': button,
                'pwa-install-ios-hint': iosHint,
                'offline-status-banner': banner,
            },
            mobileMatches: false,
            online: false,
        });

        const cleanup = await initializePwa(() => undefined);

        expect(button.classList.classes.has('hidden')).toBe(true);
        expect(iosHint.classList.classes.has('hidden')).toBe(true);
        expect(banner.classList.classes.has('hidden')).toBe(false);
        expect(showToast).toHaveBeenCalledWith('Вы оффлайн. Часть действий временно недоступна.', 'info');

        cleanup();
    });

    it('shows the offline banner again when the network comes back', async () => {
        const registration = createRegistration(vi.fn().mockResolvedValue(undefined));
        const banner = new FakeElement();
        const browser = setupBrowserGlobals(registration, {
            elements: {
                'offline-status-banner': banner,
            },
            online: false,
        });

        const cleanup = await initializePwa(() => undefined);
        browser.navigatorStub.onLine = true;
        await browser.dispatchWindow('online');

        expect(banner.classList.classes.has('hidden')).toBe(true);
        expect(showToast).toHaveBeenCalledWith('Сеть восстановлена', 'success');

        cleanup();
    });

    it('registers the service worker with the build version query string', async () => {
        const update = vi.fn().mockResolvedValue(undefined);
        const registration = createRegistration(update);
        const browser = setupBrowserGlobals(registration, {
            buildVersion: '20240628-1234',
        });

        const cleanup = await initializePwa(() => undefined);

        expect(navigator.serviceWorker.register).toHaveBeenCalledWith('/sw.js?v=20240628-1234');

        cleanup();
        expect(browser.intervalIds.size).toBe(0);
    });

    it('shows ios install guidance when the install button is tapped', async () => {
        const registration = createRegistration(vi.fn().mockResolvedValue(undefined));
        const button = new FakeElement();
        const iosHint = new FakeElement();
        setupBrowserGlobals(registration, {
            elements: {
                'pwa-install-btn': button,
                'pwa-install-ios-hint': iosHint,
            },
            userAgent: 'iPhone',
            mobileMatches: true,
        });

        const cleanup = await initializePwa(() => undefined);
        await button.dispatch('click');

        expect(button.classList.classes.has('hidden')).toBe(false);
        expect(iosHint.textContent).toContain('На экран Домой');

        cleanup();
    });

    it('accepts the install prompt on android when the deferred prompt is available', async () => {
        const registration = createRegistration(vi.fn().mockResolvedValue(undefined));
        const button = new FakeElement();
        const iosHint = new FakeElement();
        const browser = setupBrowserGlobals(registration, {
            elements: {
                'pwa-install-btn': button,
                'pwa-install-ios-hint': iosHint,
            },
            userAgent: 'Android',
            mobileMatches: true,
        });

        const prompt = vi.fn().mockResolvedValue(undefined);
        const userChoice = Promise.resolve({ outcome: 'accepted' as const });
        const beforeInstallPrompt = {
            preventDefault: vi.fn(),
            prompt,
            userChoice,
        } as never;

        const cleanup = await initializePwa(() => undefined);
        await browser.dispatchWindow('beforeinstallprompt', beforeInstallPrompt as Event);
        await button.dispatch('click');

        expect(prompt).toHaveBeenCalledTimes(1);
        expect(button.classList.classes.has('hidden')).toBe(true);
        expect(iosHint.classList.classes.has('hidden')).toBe(true);
        expect(showToast).toHaveBeenCalledWith('Приложение установлено', 'success');

        cleanup();
    });

    it('shows install guidance when no deferred prompt is available', async () => {
        const registration = createRegistration(vi.fn().mockResolvedValue(undefined));
        const button = new FakeElement();
        const iosHint = new FakeElement();
        setupBrowserGlobals(registration, {
            elements: {
                'pwa-install-btn': button,
                'pwa-install-ios-hint': iosHint,
            },
            userAgent: 'Android',
            mobileMatches: true,
        });

        const cleanup = await initializePwa(() => undefined);
        await button.dispatch('click');

        expect(showToast).toHaveBeenCalledWith('Откройте меню браузера и выберите "Установить приложение".', 'info');
        expect(button.classList.classes.has('hidden')).toBe(false);

        cleanup();
    });

    it('clears localhost caches before service worker registration', async () => {
        const update = vi.fn().mockResolvedValue(undefined);
        const registration = createRegistration(update);
        const caches = {
            keys: vi.fn().mockResolvedValue(['cache-a', 'cache-b']),
            delete: vi.fn().mockResolvedValue(true),
        };
        setupBrowserGlobals(registration, {
            hostname: 'localhost',
            caches,
        });

        const cleanup = await initializePwa(() => undefined);

        expect(caches.keys).toHaveBeenCalledTimes(1);
        expect(caches.delete).toHaveBeenCalledWith('cache-a');
        expect(caches.delete).toHaveBeenCalledWith('cache-b');

        cleanup();
    });

    it('logs cache cleanup failures on localhost', async () => {
        const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
        const registration = createRegistration(vi.fn().mockResolvedValue(undefined));
        const caches = {
            keys: vi.fn().mockRejectedValue(new Error('cache broken')),
            delete: vi.fn().mockResolvedValue(true),
        };
        setupBrowserGlobals(registration, {
            hostname: 'localhost',
            caches,
        });

        const cleanup = await initializePwa(() => undefined);

        expect(consoleSpy).toHaveBeenCalledWith('Cache cleanup failed', expect.objectContaining({
            event: 'pwa.cache_cleanup_failed',
        }));

        cleanup();
    });

    it('returns a no-op when service workers are unavailable', async () => {
        const registration = createRegistration(vi.fn().mockResolvedValue(undefined));
        setupBrowserGlobals(registration, {
            serviceWorker: false,
        });

        const cleanup = await initializePwa(() => undefined);
        cleanup();

        expect(navigator.serviceWorker).toBeUndefined();
    });

    it('ignores updatefound events without an installing worker', async () => {
        const registration = createRegistration(vi.fn().mockResolvedValue(undefined));
        setupBrowserGlobals(registration);

        const cleanup = await initializePwa(() => undefined);
        registration.dispatch('updatefound');

        cleanup();
    });

    it('logs unexpected update-check failures', async () => {
        const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
        const update = vi.fn().mockRejectedValue(new Error('boom'));
        const registration = createRegistration(update);
        const browser = setupBrowserGlobals(registration);

        const cleanup = await initializePwa(() => undefined);
        await browser.dispatchWindow('focus');

        expect(update).toHaveBeenCalledTimes(1);
        expect(consoleSpy).toHaveBeenCalledWith('SW update check failed', expect.objectContaining({
            event: 'pwa.sw_update_check_failed',
        }));

        cleanup();
    });

    it('skips update checks while the document is hidden', async () => {
        const update = vi.fn().mockResolvedValue(undefined);
        const registration = createRegistration(update);
        const browser = setupBrowserGlobals(registration);
        browser.documentStub.visibilityState = 'hidden';

        const cleanup = await initializePwa(() => undefined);
        await browser.dispatchDocument('visibilitychange');

        expect(update).not.toHaveBeenCalled();

        cleanup();
    });

    it('runs update checks when the document becomes visible', async () => {
        const update = vi.fn().mockResolvedValue(undefined);
        const registration = createRegistration(update);
        const browser = setupBrowserGlobals(registration);
        browser.documentStub.visibilityState = 'visible';

        const cleanup = await initializePwa(() => undefined);
        await browser.dispatchDocument('visibilitychange');

        expect(update).toHaveBeenCalledTimes(1);

        cleanup();
    });

    it('runs pull-to-refresh after a qualifying swipe gesture', async () => {
        const refresh = vi.fn().mockResolvedValue(undefined);
        const registration = createRegistration(vi.fn().mockResolvedValue(undefined));
        const indicator = new FakeElement();
        const indicatorText = new FakeElement();
        const browser = setupBrowserGlobals(registration, {
            elements: {
                'pull-refresh-indicator': indicator,
                'pull-refresh-indicator-text': indicatorText,
            },
            mobileMatches: true,
            maxTouchPoints: 1,
        });

        const cleanup = await initializePwa(refresh);

        await browser.dispatchDocument('touchstart', {
            touches: [{ clientY: 10 }],
        } as unknown as Event);
        await browser.dispatchDocument('touchmove', {
            touches: [{ clientY: 100 }],
        } as unknown as Event);
        await browser.dispatchDocument('touchend');

        expect(refresh).toHaveBeenCalledTimes(1);
        expect(indicator.classList.classes.has('loading')).toBe(false);
        expect(indicatorText.textContent).toBe('Потяните для обновления');

        cleanup();
    });

    it('ignores touchmove events before a pull starts', async () => {
        const refresh = vi.fn().mockResolvedValue(undefined);
        const registration = createRegistration(vi.fn().mockResolvedValue(undefined));
        const indicator = new FakeElement();
        const indicatorText = new FakeElement();
        const browser = setupBrowserGlobals(registration, {
            elements: {
                'pull-refresh-indicator': indicator,
                'pull-refresh-indicator-text': indicatorText,
            },
            mobileMatches: true,
            maxTouchPoints: 1,
        });

        const cleanup = await initializePwa(refresh);

        await browser.dispatchDocument('touchmove', {
            touches: [{ clientY: 50 }],
        } as unknown as Event);

        expect(refresh).not.toHaveBeenCalled();
        expect(indicator.classList.classes.has('active')).toBe(false);

        cleanup();
    });

    it('resets the pull-to-refresh indicator on a short swipe', async () => {
        const refresh = vi.fn().mockResolvedValue(undefined);
        const registration = createRegistration(vi.fn().mockResolvedValue(undefined));
        const indicator = new FakeElement();
        const indicatorText = new FakeElement();
        const browser = setupBrowserGlobals(registration, {
            elements: {
                'pull-refresh-indicator': indicator,
                'pull-refresh-indicator-text': indicatorText,
            },
            mobileMatches: true,
            maxTouchPoints: 1,
        });

        const cleanup = await initializePwa(refresh);

        await browser.dispatchDocument('touchstart', {
            touches: [{ clientY: 10 }],
        } as unknown as Event);
        await browser.dispatchDocument('touchmove', {
            touches: [{ clientY: 20 }],
        } as unknown as Event);
        await browser.dispatchDocument('touchend');

        expect(refresh).not.toHaveBeenCalled();
        expect(indicator.classList.classes.has('active')).toBe(false);
        expect(indicatorText.textContent).toBe('Потяните для обновления');

        cleanup();
    });

    it('reloads the app when the service worker controller changes', async () => {
        const registration = createRegistration(vi.fn().mockResolvedValue(undefined));
        const browser = setupBrowserGlobals(registration);
        const serviceWorkerListeners = new Map<string, Listener>();

        navigator.serviceWorker.addEventListener = vi.fn((type: string, listener: Listener) => {
            serviceWorkerListeners.set(type, listener);
        });
        navigator.serviceWorker.removeEventListener = vi.fn();

        const cleanup = await initializePwa(() => undefined);
        serviceWorkerListeners.get('controllerchange')?.(new Event('controllerchange'));

        expect(browser.windowStub.location.reload).toHaveBeenCalledTimes(1);

        cleanup();
        expect(browser.intervalIds.size).toBe(0);
    });

    it('requests immediate activation when an update is installed', async () => {
        const waiting = {
            postMessage: vi.fn(),
        };
        const installingTarget = new FakeEventTarget() as FakeEventTarget & { state: string };
        installingTarget.state = 'installed';
        const registration = createRegistration(vi.fn().mockResolvedValue(undefined), {
            waiting,
            installing: installingTarget,
        });
        setupBrowserGlobals(registration);

        const cleanup = await initializePwa(() => undefined);
        registration.dispatch('updatefound');
        await installingTarget.dispatch('statechange');

        expect(waiting.postMessage).toHaveBeenCalledWith({ type: 'SKIP_WAITING' });
        expect(waiting.postMessage).toHaveBeenCalledTimes(2);

        cleanup();
    });

    it('resets the pull-to-refresh indicator when the swipe moves upward', async () => {
        const refresh = vi.fn().mockResolvedValue(undefined);
        const registration = createRegistration(vi.fn().mockResolvedValue(undefined));
        const indicator = new FakeElement();
        const indicatorText = new FakeElement();
        const browser = setupBrowserGlobals(registration, {
            elements: {
                'pull-refresh-indicator': indicator,
                'pull-refresh-indicator-text': indicatorText,
            },
            mobileMatches: true,
            maxTouchPoints: 1,
        });

        const cleanup = await initializePwa(refresh);

        await browser.dispatchDocument('touchstart', {
            touches: [{ clientY: 50 }],
        } as unknown as Event);
        await browser.dispatchDocument('touchmove', {
            touches: [{ clientY: 20 }],
        } as unknown as Event);

        expect(refresh).not.toHaveBeenCalled();
        expect(indicator.classList.classes.has('active')).toBe(false);
        expect(indicatorText.textContent).toBe('Потяните для обновления');

        cleanup();
    });

    it('logs service worker registration failures', async () => {
        const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
        const registration = createRegistration(vi.fn().mockResolvedValue(undefined));
        const browser = setupBrowserGlobals(registration);
        navigator.serviceWorker.register = vi.fn().mockRejectedValue(new Error('registration failed'));

        const cleanup = await initializePwa(() => undefined);

        expect(consoleSpy).toHaveBeenCalledWith('SW registration failed', expect.objectContaining({
            event: 'pwa.sw_registration_failed',
        }));

        cleanup();
        expect(browser.intervalIds.size).toBe(0);
    });

    it('does not request activation for non-installed updates', async () => {
        const waiting = {
            postMessage: vi.fn(),
        };
        const installingTarget = new FakeEventTarget() as FakeEventTarget & { state: string };
        installingTarget.state = 'installing';
        const registration = createRegistration(vi.fn().mockResolvedValue(undefined), {
            waiting,
            installing: installingTarget,
        });
        setupBrowserGlobals(registration);

        const cleanup = await initializePwa(() => undefined);
        registration.dispatch('updatefound');
        await installingTarget.dispatch('statechange');

        expect(waiting.postMessage).toHaveBeenCalledTimes(1);

        cleanup();
    });
});
