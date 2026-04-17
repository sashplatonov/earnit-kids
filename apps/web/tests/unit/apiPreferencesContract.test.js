const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

async function loadApiModule() {
    const modulePath = pathToFileURL(path.join(process.cwd(), 'public/js/modules/api.js')).href;
    return import(`${modulePath}?t=${Date.now()}`);
}

test('loadDataFromServer forwards childId as a query parameter', async () => {
    const originalFetch = global.fetch;
    const originalDocument = global.document;
    const calls = [];

    global.document = { cookie: '' };
    global.fetch = async (url, options) => {
        calls.push({ url, options });
        return {
            ok: true,
            json: async () => ({ tasks: [], shop: [], history: [], requests: [], children: [] })
        };
    };

    try {
        const { loadDataFromServer } = await loadApiModule();
        const response = await loadDataFromServer(2);

        assert.deepEqual(response, { tasks: [], shop: [], history: [], requests: [], children: [] });
        assert.equal(calls.length, 1);
        assert.equal(calls[0].url, '/api/data?childId=2');
        assert.equal(calls[0].options.credentials, 'same-origin');
    } finally {
        global.fetch = originalFetch;
        global.document = originalDocument;
    }
});

test('savePreference sends backend key/value payload with csrf header', async () => {
    const originalFetch = global.fetch;
    const originalDocument = global.document;
    const calls = [];

    global.document = { cookie: 'csrf_token=token%20value' };
    global.fetch = async (url, options) => {
        calls.push({ url, options });
        return { ok: true };
    };

    try {
        const { savePreference } = await loadApiModule();
        const result = await savePreference('lastSelectedChildId', 2);

        assert.equal(result, true);
        assert.equal(calls.length, 1);
        assert.equal(calls[0].url, '/api/preferences');
        assert.deepEqual(JSON.parse(calls[0].options.body), {
            key: 'lastSelectedChildId',
            value: 2
        });
        assert.equal(calls[0].options.headers['X-CSRF-Token'], 'token value');
        assert.equal(calls[0].options.credentials, 'same-origin');
    } finally {
        global.fetch = originalFetch;
        global.document = originalDocument;
    }
});