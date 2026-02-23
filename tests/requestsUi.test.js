const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const { serveStatic } = require('../src/controllers/viewController');

function createMockRequest(url, headers = {}) {
    return { url, headers };
}

function createMockResponse() {
    const state = {
        statusCode: null,
        headers: {},
        body: '',
    };

    let doneResolve;
    const done = new Promise((resolve) => {
        doneResolve = resolve;
    });

    return {
        state,
        done,
        res: {
            writeHead(statusCode, headers = {}) {
                state.statusCode = statusCode;
                state.headers = headers;
            },
            setHeader(name, value) {
                state.headers[name] = value;
            },
            end(body = '') {
                state.body = body;
                doneResolve();
            },
        },
    };
}

async function renderStatic(url) {
    const mock = createMockResponse();
    await serveStatic(createMockRequest(url), mock.res);
    await mock.done;
    return mock.state;
}

test('request rows have dedicated styles for task and purchase requests', async () => {
    const state = await renderStatic('/style.css');
    const css = state.body.toString();

    assert.equal(state.statusCode, 200);
    assert.match(css, /\.history-item--request-task\s*\{/);
    assert.match(css, /\.history-item--request-purchase\s*\{/);
});

test('request UI uses different icons for task and purchase request types', () => {
    const sourcePath = path.join(process.cwd(), 'public/js/modules/ui-requests.js');
    const source = fs.readFileSync(sourcePath, 'utf8');

    assert.match(source, /isPurchaseRequest\(req\)\s*\?\s*'🛒'\s*:\s*'📝'/);
});
