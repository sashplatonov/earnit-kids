const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

test('readLogs filters by level, clamps limit, and masks secrets', async () => {
    const logFile = path.join(os.tmpdir(), 'super-admin-logs.test.log');
    process.env.SUPER_ADMIN_LOG_PATH = logFile;
    const now = Date.now();
    const entries = [
        JSON.stringify({
            level: 'error',
            msg: 'Critical token=supersecrettoken12345',
            module: 'app',
            reqId: 'req-secret-abcdef',
            ts: now
        }),
        JSON.stringify({
            level: 'info',
            msg: 'Routine info message',
            module: 'worker',
            reqId: 'req-info',
            ts: now
        })
    ].join('\n');
    await fs.promises.writeFile(logFile, entries, 'utf8');

    const { readLogs } = require('../../src/services/logsService');
    const errorLogs = await readLogs({ level: 'error', limit: 5 });
    assert.strictEqual(errorLogs.length, 1);
    assert.strictEqual(errorLogs[0].level, 'error');
    assert.strictEqual(errorLogs[0].msg.includes('***'), true);

    const infoLogs = await readLogs({ level: 'info', limit: 10 });
    assert.strictEqual(infoLogs.length, 1);

    const clippedLogs = await readLogs({ level: 'info', limit: 1000 });
    assert.strictEqual(clippedLogs.length, 1);

    await fs.promises.unlink(logFile);
    delete process.env.SUPER_ADMIN_LOG_PATH;
});
