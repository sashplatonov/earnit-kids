import { afterEach, describe, expect, it, vi } from 'vitest';

const { logBrowser, noticeBrowserError } = vi.hoisted(() => ({
    logBrowser: vi.fn(),
    noticeBrowserError: vi.fn(),
}));

vi.mock('$lib/observability/newrelic', () => ({
    logBrowser,
    noticeBrowserError,
}));

import {
    installGlobalClientLogging,
    logClientError,
    logClientInfo,
    logClientWarn,
} from '../../src/lib/logging/clientLogger';

describe('client logging', () => {
    afterEach(() => {
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
        logBrowser.mockClear();
        noticeBrowserError.mockClear();
    });

    it('forwards direct client logs to New Relic', () => {
        vi.stubGlobal('window', {
            location: {
                href: 'https://example.com/en/app/tasks?child=7',
                pathname: '/en/app/tasks',
                search: '?child=7',
            },
        });
        vi.stubGlobal('navigator', { userAgent: 'Vitest Browser' });

        logClientInfo('ui.ready', 'App ready', { familyId: 'family-1' });
        logClientWarn('ui.warn', 'Something looks odd');
        logClientError('ui.error', 'Something failed', { error: true });

        expect(logBrowser).toHaveBeenNthCalledWith(1, 'info', 'ui.ready', 'App ready', { familyId: 'family-1' });
        expect(logBrowser).toHaveBeenNthCalledWith(2, 'warn', 'ui.warn', 'Something looks odd', undefined);
        expect(logBrowser).toHaveBeenNthCalledWith(3, 'error', 'ui.error', 'Something failed', {
            href: 'https://example.com/en/app/tasks?child=7',
            path: '/en/app/tasks',
            search: '?child=7',
            userAgent: 'Vitest Browser',
            error: true,
        });
    });

    it('does nothing when no window exists', () => {
        expect(installGlobalClientLogging()).toEqual(expect.any(Function));
        expect(logBrowser).not.toHaveBeenCalled();
        expect(noticeBrowserError).not.toHaveBeenCalled();
    });

    it('captures unhandled errors and rejections in the browser', () => {
        type BrowserEvent = {
            filename?: string;
            lineno?: number;
            colno?: number;
            message?: string;
            error?: unknown;
            reason?: unknown;
        };
        const listeners = new Map<string, (event: BrowserEvent) => void>();
        const removeEventListener = vi.fn();

        vi.stubGlobal('window', {
            addEventListener: vi.fn((type: string, listener: (event: BrowserEvent) => void) => {
                listeners.set(type, listener);
            }),
            removeEventListener,
        });

        const cleanup = installGlobalClientLogging();

        listeners.get('error')?.({
            filename: 'app.ts',
            lineno: 12,
            colno: 34,
            message: 'Boom',
            error: new Error('Boom'),
        });
        listeners.get('unhandledrejection')?.({
            reason: 'broken promise',
        });

        expect(logBrowser).toHaveBeenNthCalledWith(1, 'error', 'ui.window_error', 'Boom', expect.objectContaining({
            event: 'ui.window_error',
            source: 'app.ts',
            line: 12,
            column: 34,
        }));
        expect(noticeBrowserError).toHaveBeenNthCalledWith(1, expect.any(Error), expect.objectContaining({
            event: 'ui.window_error',
        }));
        expect(logBrowser).toHaveBeenNthCalledWith(2, 'error', 'ui.unhandled_rejection', 'Unhandled promise rejection', expect.objectContaining({
            event: 'ui.unhandled_rejection',
            reason: { value: 'broken promise' },
        }));
        expect(noticeBrowserError).toHaveBeenNthCalledWith(2, 'broken promise', expect.objectContaining({
            event: 'ui.unhandled_rejection',
        }));

        cleanup();
        expect(removeEventListener).toHaveBeenCalledTimes(2);
    });
});
