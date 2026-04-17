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
    assert.match(state.body, /<title>Вход и первые шаги \| EarnIt Kids<\/title>/);
    assert.match(state.body, /class="login-shell"/);
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
    assert.match(state.body, /<title>Вход и первые шаги \| EarnIt Kids<\/title>/);
    assert.match(state.body, /class="login-shell"/);
});

test('serveStatic maps /style.css to public css file', async () => {
    const state = await render(serveStatic, createMockRequest('/style.css'));

    assert.equal(state.statusCode, 200);
    assert.match(state.headers['Content-Type'], /text\/css/);
    assert.match(state.body.toString(), /:root\s*\{/);
});

test('serveStatic keeps mobile nav compact without viewport overflow', async () => {
    const state = await render(serveStatic, createMockRequest('/css/public-top-nav.css'));
    const css = state.body.toString();

    assert.equal(state.statusCode, 200);
    assert.match(css, /@media\s*\(max-width:\s*920px\)[\s\S]*?\.public-site-header\s*\{[\s\S]*?width:\s*calc\(100% - 1rem\);/);
    assert.match(css, /@media\s*\(max-width:\s*920px\)[\s\S]*?\.public-top-nav\s*\{[\s\S]*?width:\s*100%;/);
    assert.match(css, /@media\s*\(max-width:\s*920px\)[\s\S]*?body\s*\{[\s\S]*?padding-top:\s*8\.7rem;/);
});

test('serveStatic keeps skip link hidden until keyboard focus', async () => {
    const state = await render(serveStatic, createMockRequest('/style.css'));
    const css = state.body.toString();

    assert.equal(state.statusCode, 200);
    assert.match(css, /\.skip-link\s*\{[\s\S]*?left:\s*-9999px;/);
    assert.match(css, /\.skip-link:focus-visible\s*\{/);
});

test('serveStatic includes mobile PWA install styles', async () => {
    const state = await render(serveStatic, createMockRequest('/style.css'));
    const css = state.body.toString();

    assert.equal(state.statusCode, 200);
    assert.match(css, /\.header__actions\s*\{/);
    assert.match(css, /\.header__install\s*\{/);
    assert.match(css, /\.header__install-hint\s*\{/);
});

test('serveStatic serves PWA install module with install prompt handlers', async () => {
    const state = await render(serveStatic, createMockRequest('/js/modules/pwa-install.js'));
    const js = state.body.toString();

    assert.equal(state.statusCode, 200);
    assert.match(state.headers['Content-Type'], /application\/javascript/);
    assert.match(js, /beforeinstallprompt/);
    assert.match(js, /appinstalled/);
    assert.match(js, /Установить приложение/);
});

test('serveStatic keeps floating nav dropdown compact on mobile', async () => {
    const state = await render(serveStatic, createMockRequest('/style.css'));
    const css = state.body.toString();

    assert.equal(state.statusCode, 200);
    assert.match(css, /\.nav__dropdown\.is-floating\s*\{[\s\S]*?top:\s*auto;/);
    assert.match(css, /\.nav__dropdown\.is-floating\s*\{[\s\S]*?max-height:\s*min\(60vh,\s*calc\(100dvh - 120px\)\);/);
});

test('serveStatic blocks directory traversal attempts', async () => {
    const state = await render(serveStatic, createMockRequest('/../src/app.js'));

    assert.equal(state.statusCode, 403);
    assert.equal(state.body, 'Forbidden');
});
