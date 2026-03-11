const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

function read(relativePath) {
    return fs.readFileSync(path.join(__dirname, '../../', relativePath), 'utf8');
}

test('dialog backdrop closing no longer depends on pointer coordinates', () => {
    const source = read('public/js/modules/utils.js');

    assert.match(source, /if \(e\.target === modal\) \{\s*modal\.close\(\);/);
    assert.doesNotMatch(source, /e\.clientX < rect\.left/);
    assert.doesNotMatch(source, /e\.clientY < rect\.top/);
});
