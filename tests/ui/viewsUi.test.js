const test = require('node:test');
const assert = require('node:assert/strict');

const { serveLogin, serveResetPassword, serveVerify, serveStatic, serveIndex } = require('../../src/controllers/viewController');

function createMockRequest(url, headers = {}) {
    return {
        url,
        headers,
    };
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

async function render(handler, req) {
    const mock = createMockResponse();
    await handler(req, mock.res);
    await mock.done;
    return mock.state;
}

test('serveLogin renders login UI', async () => {
    const state = await render(serveLogin, createMockRequest('/login.html'));

    assert.equal(state.statusCode, 200);
    assert.match(state.headers['Content-Type'], /text\/html/);
    assert.match(state.body, /<title>Вход - Монетки<\/title>/);
    assert.match(state.body, /class="login-page"/);
    assert.doesNotMatch(state.body, /\{\{CLARITY_SCRIPT\}\}/);
});

test('serveResetPassword renders reset password form UI', async () => {
    const state = await render(serveResetPassword, createMockRequest('/reset-password'));

    assert.equal(state.statusCode, 200);
    assert.match(state.headers['Content-Type'], /text\/html/);
    assert.match(state.body, /<form id="resetForm">/);
    assert.match(state.body, /id="confirmPassword"/);
});

test('serveVerify renders email verification UI', async () => {
    const state = await render(serveVerify, createMockRequest('/verify'));

    assert.equal(state.statusCode, 200);
    assert.match(state.headers['Content-Type'], /text\/html/);
    assert.match(state.body, /<h2>Подтверждение Email<\/h2>/);
    assert.match(state.body, /id="loginBtn"/);
});

test('serveIndex returns login page for unauthenticated user', async () => {
    const state = await render(serveIndex, createMockRequest('/'));

    assert.equal(state.statusCode, 200);
    assert.match(state.body, /<title>Вход - Монетки<\/title>/);
    assert.match(state.body, /class="login-page"/);
});

test('serveStatic maps /style.css to public css file', async () => {
    const state = await render(serveStatic, createMockRequest('/style.css'));

    assert.equal(state.statusCode, 200);
    assert.match(state.headers['Content-Type'], /text\/css/);
    assert.match(state.body.toString(), /:root\s*\{/);
});

test('serveStatic blocks directory traversal attempts', async () => {
    const state = await render(serveStatic, createMockRequest('/../src/app.js'));

    assert.equal(state.statusCode, 403);
    assert.equal(state.body, 'Forbidden');
});
