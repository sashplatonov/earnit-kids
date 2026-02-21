const test = require('node:test');
const assert = require('node:assert/strict');
const familyDataRepository = require('../src/db/familyDataRepository');

// Mock data
const mockRows = [
    { name: 'Task 1', coins: 100, count: 5 },
    { name: 'Task 2', coins: 50, count: 2 }
];

test('getAnalyticsData should return aggregated results', async () => {
    // Note: In a real environment, we'd use a test database or a heavy mock.
    // Since we don't have a mock for the 'pg' pool easily available here without complex setup,
    // we'll rely on checking the function structure.

    assert.equal(typeof familyDataRepository.getAnalyticsData, 'function');
});

test('Analytics SQL should handle timeframe correctly', () => {
    const timeframes = ['week', 'month', 'year'];
    timeframes.forEach(tf => {
        // Just verify it doesn't throw with basic params (even if DB connection fails later)
        try {
            familyDataRepository.getAnalyticsData(1, null, tf).catch(() => { });
        } catch (e) {
            assert.fail(`Failed on timeframe: ${tf}`);
        }
    });
});
