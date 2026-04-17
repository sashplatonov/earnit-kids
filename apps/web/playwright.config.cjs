const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
    testDir: './tests/ui-e2e',
    timeout: 30_000,
    fullyParallel: false,
    workers: 1,
    reporter: 'line',
    use: {
        headless: true,
        serviceWorkers: 'block'
    },
});
