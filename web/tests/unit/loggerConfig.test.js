const test = require('node:test');
const assert = require('node:assert/strict');
const proxyquire = require('proxyquire').noCallThru();

test('logger writes directly to stdout in production', () => {
    const originalNodeEnv = process.env.NODE_ENV;
    process.env.NODE_ENV = 'production';
    let capturedStream = null;

    proxyquire('../../src/utils/logger', {
        pino: function fakePino(_options, stream) {
            capturedStream = stream;
            return {
                child(bindings) {
                    return { bindings, stream };
                }
            }
        }
    });

    assert.equal(capturedStream, process.stdout);

    if (originalNodeEnv === undefined) delete process.env.NODE_ENV;
    else process.env.NODE_ENV = originalNodeEnv;
});
