const test = require('node:test');
const assert = require('node:assert');
const viewController = require('../../src/controllers/viewController');
const seoRouter = require('../../src/routes/seoRouter');

function createMockRes() {
    let statusCode = 200;
    let headers = {};
    const chunks = [];
    return {
        writeHead(code, hdrs = {}) {
            statusCode = code;
            headers = hdrs;
        },
        end(chunk) {
            if (chunk) {
                chunks.push(typeof chunk === 'string' ? chunk : chunk.toString('utf8'));
            }
        },
        get body() {
            return chunks.join('');
        },
        get statusCode() {
            return statusCode;
        },
        get headers() {
            return headers;
        }
    };
}

async function createMockReq(path) {
    return {
        url: path,
        headers: {
            host: 'localhost:3000'
        }
    };
}

test('Landing view returns HTML for anonymous user', async () => {
    const req = await createMockReq('/');
    const res = createMockRes();
    await viewController.serveLanding(req, res);
    assert.strictEqual(res.statusCode, 200, 'Landing should return 200');
    assert.match(res.body, /EarnIt Kids/, 'Landing HTML should mention EarnIt Kids');
});

test('Sitemap includes landing and blog entries', async () => {
    const req = await createMockReq('/sitemap.xml');
    const res = createMockRes();
    const handled = await seoRouter.routeSeo('/sitemap.xml', req, res);
    assert.strictEqual(handled, true, 'Sitemap handler should claim responsibility');
    assert.strictEqual(res.statusCode, 200);
    assert.match(res.body, /<loc>https:\/\/[^<]+\/<\/loc>/, 'Should include root URL');
    assert.match(res.body, /<loc>https:\/\/[^<]+\/blog\/first-steps<\/loc>/, 'Should include blog slug entry');
});
