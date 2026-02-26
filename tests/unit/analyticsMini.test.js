const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

async function loadAnalyticsUi() {
    const modulePath = pathToFileURL(path.join(process.cwd(), 'public/js/modules/analytics-ui.js')).href;
    return import(modulePath);
}

test('computeMiniAnalytics returns clamped progress and labels', async () => {
    const { computeMiniAnalytics } = await loadAnalyticsUi();
    const result = computeMiniAnalytics({
        summary: { totalEarned: 180, netChange: 120 },
        topItems: [{ name: 'A', coins: 50 }, { name: 'B', coins: 150 }],
        trends: [
            { earned: 10 }, { earned: 0 }, { earned: 20 }, { earned: 0 },
            { earned: 5 }, { earned: 0 }, { earned: 30 }
        ],
        recommendations: [{ name: 'Уборка' }]
    });

    assert.equal(result.dayLabel, '90%');
    assert.equal(result.shopLabel, '50%');
    assert.equal(result.streakLabel, '4 дн.');
    assert.match(result.shopHint, /Уборка/);
});
