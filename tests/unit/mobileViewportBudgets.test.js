const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

test('mobile viewport budgets are applied to header and nav ratios', () => {
    const filePath = path.join(__dirname, '../../public/js/modules/main.js');
    const source = fs.readFileSync(filePath, 'utf8');

    assert.ok(source.includes("const MOBILE_LAYOUT_QUERY = '(max-width: 900px)'"));
    assert.ok(source.includes('headerBudget = Math.round(viewportHeight * 0.12)'));
    assert.ok(source.includes('navBudget = Math.round(viewportHeight * 0.10)'));
    assert.ok(source.includes("setProperty('--mobile-header-max-height'"));
    assert.ok(source.includes("setProperty('--mobile-nav-height'"));
});
