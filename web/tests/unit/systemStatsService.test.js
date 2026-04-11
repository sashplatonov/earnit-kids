const test = require('node:test');
const assert = require('node:assert');
const proxyquire = require('proxyquire');

test('getSystemOverview returns typed process and os metrics with positive uptime', () => {
    const { getSystemOverview } = proxyquire('../../src/services/systemStatsService', {
        os: {
            loadavg: () => [0.13, 0.2, 0.28],
            cpus: () => [{}, {}, {}, {}],
            totalmem: () => 1024,
            freemem: () => 256
        }
    });

    const originalMemoryUsage = process.memoryUsage;
    const originalUptime = process.uptime;
    try {
        process.memoryUsage = () => ({ rss: 123, heapUsed: 45, heapTotal: 67 });
        process.uptime = () => 42;

        const overview = getSystemOverview();

        assert.ok(overview.process.uptimeSec > 0);
        assert.strictEqual(typeof overview.process.rssBytes, 'number');
        assert.strictEqual(typeof overview.process.heapUsedBytes, 'number');
        assert.strictEqual(typeof overview.process.heapTotalBytes, 'number');

        assert.strictEqual(typeof overview.os.loadAvg1, 'number');
        assert.strictEqual(typeof overview.os.loadAvg5, 'number');
        assert.strictEqual(typeof overview.os.loadAvg15, 'number');
        assert.strictEqual(typeof overview.os.cpuCount, 'number');
        assert.strictEqual(typeof overview.os.totalMemBytes, 'number');
        assert.strictEqual(typeof overview.os.freeMemBytes, 'number');
    } finally {
        process.memoryUsage = originalMemoryUsage;
        process.uptime = originalUptime;
    }
});
