import { defineConfig } from '@playwright/test';

export default defineConfig({
    testDir: './tests/e2e',
    timeout: 30_000,
    fullyParallel: false,
    workers: 1,
    reporter: 'line',
    use: {
        baseURL: 'http://localhost:4174',
        headless: true,
        serviceWorkers: 'block',
    },
    webServer: {
        command: 'npm run build && npm run preview -- --host 0.0.0.0 --port 4174',
        url: 'http://localhost:4174',
        reuseExistingServer: true,
        timeout: 120_000,
    },
});
