const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

async function loadServerContract() {
    const modulePath = pathToFileURL(path.join(process.cwd(), 'public/js/modules/server-contract.js')).href;
    return import(`${modulePath}?t=${Date.now()}`);
}

test('normalizeHistoryEntry restores task and item ids from relatedId', async () => {
    const { normalizeHistoryEntry } = await loadServerContract();

    const earnEntry = normalizeHistoryEntry({ type: 'earn', relatedId: 101, createdAt: '2026-04-13T10:00:00Z' });
    const spendEntry = normalizeHistoryEntry({ type: 'spend', relatedId: 202, createdAt: '2026-04-13T11:00:00Z' });

    assert.equal(earnEntry.taskId, 101);
    assert.equal(earnEntry.itemId, null);
    assert.equal(spendEntry.taskId, null);
    assert.equal(spendEntry.itemId, 202);
});

test('normalizeHistoryEntry preserves legacy timestamp aliases', async () => {
    const { getCreatedAt, normalizeHistoryEntry } = await loadServerContract();

    const legacyTimestamp = '2026-04-12T08:00:00Z';
    const snakeCase = '2026-04-11T07:30:00Z';

    assert.equal(getCreatedAt({ timestamp: legacyTimestamp }), legacyTimestamp);
    assert.equal(getCreatedAt({ created_at: snakeCase }), snakeCase);
    assert.equal(normalizeHistoryEntry({ type: 'earn', relatedId: 101, timestamp: legacyTimestamp }).createdAt, legacyTimestamp);
});
