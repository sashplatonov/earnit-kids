const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

test('main tabs keep visible active state for dropdown sections', () => {
    const filePath = path.join(__dirname, '../../public/js/modules/main-tabs.js');
    const source = fs.readFileSync(filePath, 'utf8');

    assert.match(source, /function syncActiveNavigationState\(tabButtons,\s*moreBtn\)/);
    assert.match(source, /moreBtn\.classList\.toggle\('active',\s*!hasPrimaryActive && hasDropdownActive\)/);
    assert.match(source, /toggleTab\(tabButtons,\s*name,\s*moreBtn\)/);
});

test('main tabs reset three-dots dropdown state on init and pageshow', () => {
    const filePath = path.join(__dirname, '../../public/js/modules/main-tabs.js');
    const source = fs.readFileSync(filePath, 'utf8');

    assert.match(source, /const resetMoreMenuState = \(\) => closeDropdowns\(moreBtn,\s*moreDropdown\)/);
    assert.match(source, /resetMoreMenuState\(\);/);
    assert.match(source, /window\.addEventListener\('pageshow',\s*resetMoreMenuState\)/);
});
