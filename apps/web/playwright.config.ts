import { defineConfig } from '@playwright/test';

const usePreviewServer = process.env.PLAYWRIGHT_USE_PREVIEW === 'true';
const baseURL = usePreviewServer
    ? 'http://e2e.localhost:4174'
    : (process.env.PLAYWRIGHT_BASE_URL ?? process.env.APP_URL ?? 'http://localhost:5001');
const chromiumExecutablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH;

export default defineConfig({
    testDir: './tests/e2e',
    timeout: 30_000,
    fullyParallel: false,
    workers: 1,
    reporter: 'line',
    use: {
        baseURL,
        headless: true,
        serviceWorkers: 'allow',
        // Default to Russian locale for the existing tests; allow override with PLAYWRIGHT_LOCALE
        locale: process.env.PLAYWRIGHT_LOCALE ?? 'ru-RU',
        ...(chromiumExecutablePath ? {
            launchOptions: {
                executablePath: chromiumExecutablePath,
            },
        } : {}),
    },
    webServer: usePreviewServer
        ? [{
            command: 'node tests/e2e/e2eBackend.mjs',
            url: 'http://127.0.0.1:18080/api/page-data/session',
            reuseExistingServer: false,
            timeout: 30_000,
        }, {
            command: 'APP_URL=http://e2e.localhost:4174 PUBLIC_BASE_URL=http://e2e.localhost:4174 BACKEND_ORIGIN=http://127.0.0.1:18080 npm run build && APP_URL=http://e2e.localhost:4174 PUBLIC_BASE_URL=http://e2e.localhost:4174 BACKEND_ORIGIN=http://127.0.0.1:18080 npm run preview -- --host 127.0.0.1 --port 4174',
            url: 'http://127.0.0.1:4174',
            reuseExistingServer: false,
            timeout: 120_000,
        }]
        : undefined,
});
