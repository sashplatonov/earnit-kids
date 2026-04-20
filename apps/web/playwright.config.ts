import { defineConfig } from '@playwright/test';

const usePreviewServer = process.env.PLAYWRIGHT_USE_PREVIEW === 'true';
const baseURL = usePreviewServer
    ? 'http://127.0.0.1:4174'
    : (process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:3001');

export default defineConfig({
    testDir: './tests/e2e',
    timeout: 30_000,
    fullyParallel: false,
    workers: 1,
    reporter: 'line',
    use: {
        baseURL,
        headless: true,
        serviceWorkers: 'block',
    },
    webServer: usePreviewServer
        ? {
            command: 'APP_URL=http://127.0.0.1:4174 PUBLIC_BASE_URL=http://127.0.0.1:4174 npm run build && APP_URL=http://127.0.0.1:4174 PUBLIC_BASE_URL=http://127.0.0.1:4174 npm run preview -- --host 127.0.0.1 --port 4174',
            url: 'http://127.0.0.1:4174',
            reuseExistingServer: false,
            timeout: 120_000,
        }
        : undefined,
});
