const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

function read(relativePath) {
    return fs.readFileSync(path.join(__dirname, '../../', relativePath), 'utf8');
}

test('scheduleSave sends explicit currentChildId to /api/data', () => {
    const source = read('public/js/modules/action-helpers.js');

    assert.match(source, /saveDataToServer\(\{[\s\S]*childId:\s*state\.currentChildId,[\s\S]*tasks:\s*state\.tasks,[\s\S]*shop:\s*state\.shopItems,/);
});
