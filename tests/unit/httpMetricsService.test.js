const test = require('node:test');
const assert = require('node:assert');
const proxyquire = require('proxyquire');

test('getHttpMetrics aggregates counters and durations', async () => {
    const snapshot = {
        http_requests_total: new Map([
            ['GET:/api/data:200', 5],
            ['GET:/api/data:500', 2],
            ['POST:/api/store:200', 3]
        ]),
        http_request_duration_ms_bucket: new Map([
            ['GET:/api/data', 350],
            ['POST:/api/store', 90]
        ]),
        http_requests_errors_total: new Map([
            ['GET:/api/data:500', 2]
        ])
    };

    const { getHttpMetrics } = proxyquire('../../src/services/httpMetricsService', {
        '../utils/metrics': {
            getMetricSnapshot: () => snapshot
        }
    });

    const metrics = await getHttpMetrics();
    assert.strictEqual(metrics.summary.requestsTotal, 10);
    assert.strictEqual(metrics.summary.errorsTotal, 2);
    assert.ok(metrics.summary.errorRatePct >= 0);
    assert.ok(metrics.topEndpoints.length >= 2);
    const getDataEntry = metrics.topEndpoints.find(e => e.method === 'GET' && e.path === '/api/data');
    assert.ok(getDataEntry);
    assert.strictEqual(getDataEntry.errors, 2);
    assert.strictEqual(getDataEntry.avgDurationMs, Math.round(350 / 7));
    assert.strictEqual(metrics.latency.p95Ms, null);
    assert.strictEqual(metrics.latency.p99Ms, null);
});
