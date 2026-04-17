const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

function read(relativePath) {
    return fs.readFileSync(path.join(__dirname, '../../', relativePath), 'utf8');
}

test('scheduleSave sends explicit currentChildId to /api/data', () => {
    const source = read('public/js/modules/action-helpers.js');

    assert.match(source, /function buildSavePayload\(\) \{[\s\S]*childId:\s*state\.currentChildId,[\s\S]*balance:\s*state\.balance,[\s\S]*tasks:\s*state\.tasks,[\s\S]*shop:\s*state\.shopItems,[\s\S]*history:\s*state\.history,[\s\S]*requests:\s*state\.requests,[\s\S]*children:\s*state\.children/);
});

test('child switches and server refreshes flush pending saves first', () => {
    const adminChildren = read('public/js/modules/admin-children.js');
    const mainInit = read('public/js/modules/main-init.js');

    assert.match(adminChildren, /export async function switchChild\([\s\S]*await flushPendingSave\(\);/);
    assert.match(mainInit, /export async function refreshFromServerAndRender\([\s\S]*await flushPendingSave\(\);/);
});

test('page lifecycle hooks force a keepalive flush for pending saves', () => {
    const mainSource = read('public/js/modules/main.js');

    assert.match(mainSource, /flushPendingSave\(\{ keepalive: true \}\)/);
    assert.match(mainSource, /window\.addEventListener\('pagehide',\s*flushWithKeepalive\)/);
    assert.match(mainSource, /window\.addEventListener\('beforeunload',\s*flushWithKeepalive\)/);
});

test('balance-changing modules use dedicated backend action endpoints instead of scheduleSave', () => {
    const apiSource = read('public/js/modules/api.js');
    const taskActions = read('public/js/modules/action-tasks.js');
    const shopActions = read('public/js/modules/action-shop.js');
    const requestActions = read('public/js/modules/action-requests.js');
    const historyActions = read('public/js/modules/action-history.js');
    const adminActions = read('public/js/modules/action-admin.js');

    assert.match(apiSource, /\/api\/tasks\/\$\{encodeURIComponent\(taskId\)\}\/complete/);
    assert.match(apiSource, /\/api\/tasks\/\$\{encodeURIComponent\(taskId\)\}\/request/);
    assert.match(apiSource, /\/api\/shop\/\$\{encodeURIComponent\(itemId\)\}\/purchase/);
    assert.match(apiSource, /\/api\/shop\/\$\{encodeURIComponent\(itemId\)\}\/request/);
    assert.match(apiSource, /\/api\/requests\/\$\{encodeURIComponent\(requestId\)\}\/approve/);
    assert.match(apiSource, /\/api\/requests\/\$\{encodeURIComponent\(requestId\)\}\/reject/);
    assert.match(apiSource, /\/api\/history\/\$\{encodeURIComponent\(historyEntryId\)\}/);
    assert.match(apiSource, /\/api\/balance\/adjust/);

    assert.doesNotMatch(taskActions, /scheduleSave\(/);
    assert.doesNotMatch(shopActions, /scheduleSave\(/);
    assert.doesNotMatch(requestActions, /scheduleSave\(/);
    assert.doesNotMatch(historyActions, /scheduleSave\(/);
    assert.doesNotMatch(adminActions, /scheduleSave\(/);
});
