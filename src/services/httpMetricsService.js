/** @file HTTP metrics aggregation for the super-admin dashboard */
const { getMetricSnapshot } = require('../utils/metrics');

function buildEndpointKey(method, path) {
    return `${method}:${path}`;
}

function normalizeEndpointData(metricsSnapshot) {
    const endpoints = new Map();
    for (const [key, count] of metricsSnapshot.http_requests_total) {
        const [method, path] = key.split(':');
        const baseKey = buildEndpointKey(method, path);
        const current = endpoints.get(baseKey) || { method, path, count: 0, errors: 0 };
        current.count += count;
        endpoints.set(baseKey, current);
    }

    for (const [key, errors] of metricsSnapshot.http_requests_errors_total) {
        const [method, path] = key.split(':');
        const baseKey = buildEndpointKey(method, path);
        const current = endpoints.get(baseKey);
        if (current) {
            current.errors += errors;
        }
    }

    for (const [key, durationSum] of metricsSnapshot.http_request_duration_ms_bucket) {
        const [method, path] = key.split(':');
        const baseKey = buildEndpointKey(method, path);
        const current = endpoints.get(baseKey);
        if (current) {
            current.durationSum = durationSum;
        }
    }

    return endpoints;
}

function summarizeEndpoints(endpoints) {
    return Array.from(endpoints.values())
        .sort((a, b) => b.count - a.count)
        .slice(0, 20)
        .map((entry) => ({
            method: entry.method,
            path: entry.path,
            count: entry.count,
            errors: entry.errors || 0,
            avgDurationMs: entry.count && entry.durationSum ? Math.round(entry.durationSum / entry.count) : null
        }));
}

function getSummaryStatistics(metricsSnapshot) {
    const totalRequests = Array.from(metricsSnapshot.http_requests_total.values())
        .reduce((sum, cur) => sum + cur, 0);
    const totalErrors = Array.from(metricsSnapshot.http_requests_errors_total.values())
        .reduce((sum, cur) => sum + cur, 0);
    const errorRatePct = totalRequests ? parseFloat(((totalErrors / totalRequests) * 100).toFixed(2)) : 0;
    return {
        summary: {
            requestsTotal: totalRequests,
            errorsTotal: totalErrors,
            errorRatePct
        }
    };
}

async function getHttpMetrics() {
    const metricsSnapshot = getMetricSnapshot();
    const endpoints = normalizeEndpointData(metricsSnapshot);
    const summary = getSummaryStatistics(metricsSnapshot);

    return {
        ...summary,
        topEndpoints: summarizeEndpoints(endpoints),
        latency: {
            p95Ms: null,
            p99Ms: null
        }
    };
}

module.exports = {
    getHttpMetrics
};
