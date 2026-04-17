const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

async function loadFilters() {
    const modulePath = pathToFileURL(path.join(process.cwd(), 'public/js/modules/super-admin-filters.js')).href;
    return import(modulePath);
}

test('applyFamiliesFilters filters by status and search', async () => {
    const { applyFamiliesFilters } = await loadFilters();
    const families = [
        { id: 'fam-1', email: 'one@example.com', isBlocked: false, created_at: '2026-01-01T00:00:00Z' },
        { id: 'fam-2', email: 'two@example.com', isBlocked: true, created_at: '2026-02-01T00:00:00Z' }
    ];

    const result = applyFamiliesFilters(families, { status: 'blocked', sort: 'created', search: 'two' });
    assert.equal(result.length, 1);
    assert.equal(result[0].id, 'fam-2');
});

test('applyFamiliesFilters sorts by last activity', async () => {
    const { applyFamiliesFilters } = await loadFilters();
    const families = [
        { id: 'fam-1', email: 'one@example.com', isBlocked: false, last_activity: '2026-02-20T10:00:00Z' },
        { id: 'fam-2', email: 'two@example.com', isBlocked: false, last_activity: '2026-02-24T10:00:00Z' }
    ];

    const result = applyFamiliesFilters(families, { status: 'all', sort: 'active', search: '' });
    assert.equal(result[0].id, 'fam-2');
});
