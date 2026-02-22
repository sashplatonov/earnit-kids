const limits = new Map();

// 100 requests per IP per minute
const RATE_LIMIT_MS = 60000;
const MAX_REQUESTS = process.env.RATE_LIMIT_MAX || 100;

function rateLimit(req) {
    const ip = req.headers['x-forwarded-for'] || req.socket.remoteAddress;
    const now = Date.now();

    if (!limits.has(ip)) {
        limits.set(ip, { count: 1, resetAt: now + RATE_LIMIT_MS });
        return false;
    }

    const record = limits.get(ip);

    if (now > record.resetAt) {
        record.count = 1;
        record.resetAt = now + RATE_LIMIT_MS;
        return false;
    }

    record.count++;
    if (record.count > MAX_REQUESTS) {
        return true; // Limit exceeded
    }

    return false;
}

// Clean up old entries periodically
setInterval(() => {
    const now = Date.now();
    for (const [ip, record] of limits.entries()) {
        if (now > record.resetAt) {
            limits.delete(ip);
        }
    }
}, RATE_LIMIT_MS);

module.exports = { rateLimit };
