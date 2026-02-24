/** @file Metrics utility helpers */
/**
 * Simple metrics collector for Prometheus format
 */

const metrics = {
    http_requests_total: new Map(), // key: 'method,path,status' -> count
    http_request_duration_ms_bucket: new Map(), // key: 'method,path' -> sum of durations
    http_requests_errors_total: new Map(), // key: 'method,path,code' -> count
};

/**
 * Record a request metric
 * @param {object} data - Metric data
 */
function recordRequest({ method, path, status, duration }) {
    // Normalize path (remove IDs)
    const normalizedPath = path.split('?')[0].replace(/\/\d+(\/|$)/g, '/:id$1');
    const key = `${method}:${normalizedPath}:${status}`;
    metrics.http_requests_total.set(key, (metrics.http_requests_total.get(key) || 0) + 1);

    const durationKey = `${method}:${normalizedPath}`;
    metrics.http_request_duration_ms_bucket.set(durationKey, (metrics.http_request_duration_ms_bucket.get(durationKey) || 0) + duration);

    if (status >= 400) {
        const errorKey = `${method}:${normalizedPath}:${status}`;
        metrics.http_requests_errors_total.set(errorKey, (metrics.http_requests_errors_total.get(errorKey) || 0) + 1);
    }
}

/**
 * Generate metrics in Prometheus text format
 */
function generateMetrics() {
    let output = '';

    const addCounter = ({ name, help, map, labels }) => {
        output += `# HELP ${name} ${help}\n# TYPE ${name} counter\n`;
        for (const [key, value] of map) {
            const vals = key.split(':');
            const labelStr = labels.map((l, i) => `${l}="${vals[i]}"`).join(',');
            output += `${name}{${labelStr}} ${value}\n`;
        }
        output += '\n';
    };

    addCounter({ name: 'http_requests_total', help: 'Total number of HTTP requests', map: metrics.http_requests_total, labels: ['method', 'path', 'status'] });
    addCounter({ name: 'http_request_duration_ms_sum', help: 'Total duration of HTTP requests in ms', map: metrics.http_request_duration_ms_bucket, labels: ['method', 'path'] });
    addCounter({ name: 'http_requests_errors_total', help: 'Total number of HTTP errors', map: metrics.http_requests_errors_total, labels: ['method', 'path', 'status'] });

    // Add process metrics
    output += `# HELP process_uptime_seconds Uptime of the process in seconds\n# TYPE process_uptime_seconds gauge\nprocess_uptime_seconds ${process.uptime()}\n\n`;
    output += `# HELP process_memory_rss_bytes Resident set size in bytes\n# TYPE process_memory_rss_bytes gauge\nprocess_memory_rss_bytes ${process.memoryUsage().rss}\n`;

    return output;
}

module.exports = {
    recordRequest,
    generateMetrics
};
