const test = require('node:test');
const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');

const parseBody = require('../src/middleware/body-parser');

class MockRequest extends EventEmitter {}

test('parseBody parses valid JSON payload', async () => {
    const req = new MockRequest();
    const bodyPromise = parseBody(req);

    req.emit('data', '{"name":"kid","coins":10}');
    req.emit('end');

    const parsed = await bodyPromise;
    assert.deepEqual(parsed, { name: 'kid', coins: 10 });
});

test('parseBody resolves empty object for empty payload', async () => {
    const req = new MockRequest();
    const bodyPromise = parseBody(req);

    req.emit('end');

    const parsed = await bodyPromise;
    assert.deepEqual(parsed, {});
});

test('parseBody rejects invalid JSON payload', async () => {
    const req = new MockRequest();
    const bodyPromise = parseBody(req);

    req.emit('data', '{"broken":');
    req.emit('end');

    await assert.rejects(bodyPromise, /Invalid JSON/);
});

test('parseBody forwards stream errors', async () => {
    const req = new MockRequest();
    const bodyPromise = parseBody(req);

    req.emit('error', new Error('stream failure'));

    await assert.rejects(bodyPromise, /stream failure/);
});
