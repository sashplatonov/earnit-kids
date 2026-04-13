const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

test('main tabs reset scroll position on mobile tab switch', () => {
    const filePath = path.join(__dirname, '../../public/js/modules/main-tabs.js');
    const source = fs.readFileSync(filePath, 'utf8');

    assert.match(source, /MOBILE_LAYOUT_QUERY\s*=\s*'\(max-width:\s*900px\)'/);
    assert.match(source, /matchMedia\(MOBILE_LAYOUT_QUERY\)\.matches/);
    assert.match(source, /mainContent(?:\?\.|\.)scrollTo\(\{\s*top:\s*0,\s*behavior:\s*'auto'\s*\}\)/);
    assert.match(source, /window\.scrollTo\(\{\s*top:\s*0,\s*behavior:\s*'auto'\s*\}\)/);
});
