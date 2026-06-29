import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const browserAgentCtor = vi.fn();

vi.mock('@newrelic/browser-agent/loaders/browser-agent', () => ({
    BrowserAgent: browserAgentCtor,
}));

function stubWindow(api?: Record<string, unknown>) {
    vi.stubGlobal('window', {
        location: {
            pathname: '/app/tasks',
        },
        newrelic: api,
    });
}

async function loadModule() {
    vi.resetModules();
    return import('../../src/lib/observability/newrelic');
}

describe('browser observability', () => {
    beforeEach(() => {
        browserAgentCtor.mockClear();
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
        vi.unstubAllEnvs();
    });

    it('skips initialization when New Relic is disabled', async () => {
        vi.stubEnv('VITE_NEW_RELIC_BROWSER_ENABLED', 'false');
        stubWindow();

        const { initNewRelicBrowser } = await loadModule();
        await initNewRelicBrowser();

        expect(browserAgentCtor).not.toHaveBeenCalled();
    });

    it('initializes the browser agent and records page context', async () => {
        const log = vi.fn();
        const noticeError = vi.fn();
        const setPageViewName = vi.fn();
        const setCustomAttribute = vi.fn();

        vi.stubEnv('VITE_NEW_RELIC_BROWSER_ENABLED', 'true');
        vi.stubEnv('VITE_NEW_RELIC_BROWSER_INFO', JSON.stringify({
            applicationID: 'app-123',
            licenseKey: 'license-456',
        }));
        vi.stubEnv('VITE_NEW_RELIC_BROWSER_INIT', JSON.stringify({ tracing: { enabled: false } }));
        vi.stubEnv('VITE_NEW_RELIC_BROWSER_LOADER_CONFIG', JSON.stringify({ accountID: 'acct-789' }));
        vi.stubEnv('MODE', 'qa');
        stubWindow({
            log,
            noticeError,
            setPageViewName,
            setCustomAttribute,
        });

        const { initNewRelicBrowser, logBrowser, noticeBrowserError, setBrowserPageViewName } = await loadModule();

        await initNewRelicBrowser();
        setBrowserPageViewName('/app/custom');
        logBrowser('info', 'ui.ready', 'App ready', { familyId: 'family-1' });
        logBrowser('error', 'ui.failed', 'Something failed', { count: 3 });
        noticeBrowserError('boom', { taskId: 7 });

        expect(browserAgentCtor).toHaveBeenCalledWith(expect.objectContaining({
            info: {
                applicationID: 'app-123',
                licenseKey: 'license-456',
            },
            init: expect.objectContaining({
                logging: { enabled: true },
                tracing: { enabled: false },
            }),
            loader_config: {
                accountID: 'acct-789',
            },
        }));
        expect(setPageViewName).toHaveBeenNthCalledWith(1, '/app/tasks');
        expect(setPageViewName).toHaveBeenNthCalledWith(2, '/app/custom');
        expect(setCustomAttribute).toHaveBeenCalledWith('deployment.environment', 'qa', true);
        expect(log).toHaveBeenNthCalledWith(1, 'App ready', expect.objectContaining({
            level: 'info',
            customAttributes: expect.objectContaining({
                event: 'ui.ready',
                path: '/app/tasks',
                familyId: 'family-1',
            }),
        }));
        expect(log).toHaveBeenNthCalledWith(2, 'Something failed', expect.objectContaining({
            level: 'error',
            customAttributes: expect.objectContaining({
                event: 'ui.failed',
                path: '/app/tasks',
                count: 3,
            }),
        }));
        expect(noticeError).toHaveBeenCalledWith(expect.any(Error), { taskId: 7 });
    });
});
