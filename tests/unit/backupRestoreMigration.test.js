const test = require('node:test');
const assert = require('node:assert');
const proxyquire = require('proxyquire').noCallThru();
const EventEmitter = require('node:events');

function createResponseMock() {
    return {
        statusCode: 0,
        body: null,
        writeHead(statusCode) {
            this.statusCode = statusCode;
        },
        end(payload) {
            this.body = payload ? JSON.parse(payload) : null;
        }
    };
}

function createBackupServiceState() {
    return {
        commands: [],
        migrateCalls: 0
    };
}

function loadBackupService(state) {
    return proxyquire('../../src/services/backupService', {
        child_process: {
            exec(command, callback) {
                state.commands.push(command);
                callback(null, '', '');
            }
        },
        fs: {
            writeFileSync() {},
            unlink(_path, callback) {
                callback();
            }
        },
        os: {
            tmpdir() {
                return '/tmp';
            }
        },
        pg: {
            Client: class MockClient {}
        },
        '../config': {
            DATA_DIR: '/tmp',
            TELEGRAM: { ENABLED: false }
        },
        '../utils/logger': {
            info() {},
            error() {},
            warn() {},
            debug() {}
        },
        '../utils/alerts': {
            sendTelegramDocument: async () => true
        },
        '../utils/controllerUtils': {
            sendJSON(res, data, status = 200) {
                res.writeHead(status, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify(data));
            }
        },
        '../../scripts/migrate': {
            async migrate() {
                state.migrateCalls += 1;
            }
        }
    });
}

async function runRestoreBackup(restoreBackup, res) {
    const req = new EventEmitter();
    const restorePromise = restoreBackup(req, res);

    req.emit('data', Buffer.from('legacy-backup'));
    req.emit('end');

    await restorePromise;
}

test('restoreBackup applies pending migrations after restoring an old dump', async () => {
    process.env.DATABASE_URL = 'postgres://restore:test@localhost:5432/earnit';

    const state = createBackupServiceState();
    const { restoreBackup } = loadBackupService(state);
    const res = createResponseMock();

    await runRestoreBackup(restoreBackup, res);

    assert.strictEqual(state.commands.length, 1);
    assert.match(state.commands[0], /^pg_restore --clean --if-exists --no-owner --no-privileges -d "postgres:\/\/restore:test@localhost:5432\/earnit" "\/tmp\/restore-\d+\.dump"$/);
    assert.strictEqual(state.migrateCalls, 1);
    assert.strictEqual(res.statusCode, 200);
    assert.deepStrictEqual(res.body, { success: true });
});
