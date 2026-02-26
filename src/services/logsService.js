/** @file Log retrieval and sanitization for the system dashboard */
const fs = require('fs').promises;
const path = require('path');

const DEFAULT_LOG_PATH = path.join(process.cwd(), 'logs', 'app.log');
const LEVELS = new Set(['all', 'info', 'warn', 'error']);
const MIN_LIMIT = 1;
const MAX_LIMIT = 500;
const TAIL_CHUNK_BYTES = 64 * 1024;

function clamp(value, min, max) {
    if (Number.isNaN(value)) return min;
    return Math.min(Math.max(value, min), max);
}

function normalizeLevel(level) {
    if (!level) return 'all';
    const normalized = String(level).toLowerCase();
    return LEVELS.has(normalized) ? normalized : 'all';
}

function maskSecrets(value) {
    if (typeof value !== 'string') return value;
    let sanitized = value.replace(/(Authorization|token|password|secret|cookie)(["']?\s*[:=]\s*)([^,\s]+)/gi, '$1$2***');
    sanitized = sanitized.replace(/\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/gi, '***@***');
    sanitized = sanitized.replace(/\b[A-Za-z0-9_-]{12,}\b/g, '***');
    return sanitized;
}

function parseLogJson(line) {
    try {
        return JSON.parse(line);
    } catch {
        return null;
    }
}

function fallbackLogEntry(line) {
    return {
        level: 'info',
        msg: maskSecrets(line),
        module: '',
        reqId: null,
        ts: new Date().toISOString()
    };
}

function sanitizeTimestamp(value) {
    if (!value) {
        return new Date().toISOString();
    }
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? new Date().toISOString() : parsed.toISOString();
}

function buildLogEntry(parsed) {
    const tsValue = parsed.ts ?? parsed.time;
    const timestamp = sanitizeTimestamp(tsValue);
    const level = normalizeLevel(parsed.level);
    return {
        msg: maskSecrets(parsed.msg || parsed.message || ''),
        module: maskSecrets(parsed.module || parsed.logger || ''),
        level,
        reqId: maskSecrets(parsed.reqId || parsed.req_id || parsed.requestId || ''),
        ts: timestamp
    };
}

function normalizeLogLine(line) {
    const parsed = parseLogJson(line);
    return parsed ? buildLogEntry(parsed) : fallbackLogEntry(line);
}

async function readTailContent(logPath, expectedLines) {
    const handle = await fs.open(logPath, 'r');
    try {
        const stats = await handle.stat();
        let position = stats.size;
        let content = '';
        let newlineCount = 0;
        const targetLines = Math.max(expectedLines * 2, expectedLines + 20);

        while (position > 0 && newlineCount <= targetLines) {
            const chunkSize = Math.min(TAIL_CHUNK_BYTES, position);
            position -= chunkSize;
            const buffer = Buffer.alloc(chunkSize);
            await handle.read(buffer, 0, chunkSize, position);
            const chunkText = buffer.toString('utf8');
            content = chunkText + content;
            newlineCount += (chunkText.match(/\n/g) || []).length;
        }

        return content;
    } finally {
        await handle.close();
    }
}

async function readLogs(options = {}) {
    const level = normalizeLevel(options.level);
    const limit = clamp(parseInt(options.limit, 10) || MIN_LIMIT, MIN_LIMIT, MAX_LIMIT);
    const logPath = process.env.SUPER_ADMIN_LOG_PATH || DEFAULT_LOG_PATH;

    let content;
    try {
        content = await readTailContent(logPath, limit);
    } catch {
        return [];
    }

    const lines = content.split('\n').filter(Boolean);
    const result = [];

    for (let i = lines.length - 1; i >= 0 && result.length < limit; i--) {
        const entry = normalizeLogLine(lines[i]);
        if (level !== 'all' && entry.level !== level) continue;
        result.push(entry);
    }

    return result;
}

module.exports = {
    readLogs
};
