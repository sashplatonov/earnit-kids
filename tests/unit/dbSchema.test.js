const test = require('node:test');
const assert = require('node:assert');
const proxyquire = require('proxyquire').noCallThru();

test('schema helper derives default schema from project name', () => {
    delete process.env.DB_SCHEMA;

    const schema = proxyquire('../../src/db/schema', {
        '../../package.json': { name: 'earnit-kids' }
    });

    assert.strictEqual(schema.getDatabaseSchema(), 'earnit_kids');
    assert.strictEqual(schema.getSearchPath(), 'earnit_kids,public');
});

test('schema helper normalizes custom DB_SCHEMA values', () => {
    process.env.DB_SCHEMA = 'EarnIt Kids API';

    const schema = proxyquire('../../src/db/schema', {
        '../../package.json': { name: 'ignored-project-name' }
    });

    assert.strictEqual(schema.getDatabaseSchema(), 'earnit_kids_api');
    assert.strictEqual(schema.quoteIdentifier(schema.getDatabaseSchema()), '"earnit_kids_api"');

    delete process.env.DB_SCHEMA;
});

test('db connection config sets search_path to project schema first', () => {
    process.env.DATABASE_URL = 'postgres://user:pass@localhost:5432/app';
    process.env.DB_SCHEMA = 'earnit_kids';
    process.env.DB_SSL = 'false';

    let capturedConfig = null;
    proxyquire('../../src/db/connection', {
        pg: {
            Pool: class FakePool {
                constructor(config) {
                    capturedConfig = config;
                }

                on() {}
                query() {}
                connect() {}
            }
        },
        '../utils/logger': {
            createLogger: () => ({
                debug() {},
                error() {},
                warn() {},
                info() {}
            })
        }
    });

    assert.ok(capturedConfig, 'Pool config must be created');
    assert.strictEqual(capturedConfig.options, '-c search_path=earnit_kids,public');

    delete process.env.DB_SCHEMA;
    delete process.env.DB_SSL;
});
