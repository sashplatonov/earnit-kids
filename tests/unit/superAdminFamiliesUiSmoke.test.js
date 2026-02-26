const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

test('super-admin families table uses current operational columns and confirm modal', () => {
    const htmlPath = path.join(process.cwd(), 'views', 'super-admin.html');
    const html = fs.readFileSync(htmlPath, 'utf8');

    assert.ok(html.includes('<th class="hide-mobile">Email</th>'));
    assert.ok(html.includes('<th class="hide-mobile">Детей</th>'));
    assert.doesNotMatch(html, />Семья</);
    assert.doesNotMatch(html, /Лимит \(мес\)/);
    assert.doesNotMatch(html, /Admin Pass/);
    assert.match(html, /id="super-confirm-modal"/);
    assert.match(html, /id="super-confirm-ok"/);
    assert.doesNotMatch(html, /🚀 Репликация/);
});

test('families module uses delegated action buttons and custom confirm modal', () => {
    const modulePath = path.join(process.cwd(), 'public/js/modules/super-admin-families.js');
    const source = fs.readFileSync(modulePath, 'utf8');

    assert.match(source, /data-action="view-family"/);
    assert.match(source, /data-action="toggle-family-block"/);
    assert.match(source, /showSuperConfirm\(/);
});

test('system tab exposes http and logs all-level filters', () => {
    const htmlPath = path.join(process.cwd(), 'views', 'super-admin.html');
    const html = fs.readFileSync(htmlPath, 'utf8');

    assert.match(html, /data-http-filter="all"/);
    assert.match(html, /data-http-filter="errors"/);
    assert.match(html, /data-logs-level="all"/);
});

test('base catalog module renders grouped sections for card categories', () => {
    const modulePath = path.join(process.cwd(), 'public/js/modules/super-admin-base.js');
    const source = fs.readFileSync(modulePath, 'utf8');

    assert.match(source, /catalog-group/);
    assert.match(source, /catalog-group__grid/);
});
