const test = require('node:test');
const assert = require('node:assert');
const proxyquire = require('proxyquire');

test('findByChildToken does not query removed family name column', async () => {
    let capturedSql = '';
    const childRepository = proxyquire('../../src/db/childRepository', {
        './connection': {
            query: async (sql) => {
                capturedSql = sql;
                return { rows: [] };
            }
        }
    });

    await childRepository.findByChildToken('token');

    assert.ok(capturedSql, 'Запрос должен быть выполнен');
    assert.strictEqual(capturedSql.includes('f.name'), false, 'SQL не должен ссылаться на удалённое поле');
});
