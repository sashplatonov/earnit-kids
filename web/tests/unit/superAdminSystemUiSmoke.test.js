const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

test('super-admin view contains system dashboard tab and sections', () => {
    const htmlPath = path.join(process.cwd(), 'views', 'super-admin.html');
    const html = fs.readFileSync(htmlPath, 'utf8');

    assert.match(html, /id="tab-btn-system"/);
    assert.match(html, /id="tab-system"/);
    assert.match(html, /id="system-kpi-grid"/);
    assert.match(html, /id="http-metrics-body"/);
    assert.match(html, /id="logs-list"/);
});

test('system module configures split polling intervals and prevents timer duplication', () => {
    const modulePath = path.join(process.cwd(), 'public/js/modules/super-admin-system.js');
    const source = fs.readFileSync(modulePath, 'utf8');

    assert.match(source, /OVERVIEW_DB_POLL_INTERVAL\s*=\s*10000/);
    assert.match(source, /HTTP_POLL_INTERVAL\s*=\s*15000/);
    assert.match(source, /LOGS_POLL_INTERVAL\s*=\s*15000/);
    assert.match(source, /stopSystemPolling\(\);\s*\n\s*loadOverviewAndDbPanels\(\);/);
    assert.match(source, /clearInterval\(systemState\.overviewPollId\)/);
    assert.match(source, /clearInterval\(systemState\.httpPollId\)/);
    assert.match(source, /clearInterval\(systemState\.logsPollId\)/);
});
