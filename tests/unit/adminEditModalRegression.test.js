const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

function read(relativePath) {
    return fs.readFileSync(path.join(__dirname, '../../', relativePath), 'utf8');
}

test('task edit keeps original child binding when saving weekly frequency', () => {
    const source = read('public/js/modules/admin-tasks.js');

    assert.match(source, /function getEditingTask\(\)\s*\{\s*return editingTaskId \? state\.tasks\.find\(t => t\.id == editingTaskId\) : null;\s*\}/);
    assert.match(source, /function buildTaskPayload\(\)\s*\{[\s\S]*const existingTask = getEditingTask\(\);/);
    assert.match(source, /childId:\s*existingTask\?\.childId \?\? state\.currentChildId,/);
    assert.match(source, /frequency:\s*fl > 0 \? \{ limit: fl, period: document\.getElementById\('task-freq-period'\)\.value \} : null/);
});

test('shop edit keeps original child binding when saving weekly frequency', () => {
    const source = read('public/js/modules/admin-shop.js');

    assert.match(source, /function getEditingShopItem\(\)\s*\{\s*return editingShopId \? state\.shopItems\.find\(i => i\.id == editingShopId\) : null;\s*\}/);
    assert.match(source, /function buildShopPayload\(\)\s*\{[\s\S]*const existingItem = getEditingShopItem\(\);/);
    assert.match(source, /childId:\s*existingItem\?\.childId \?\? state\.currentChildId,/);
    assert.match(source, /frequency:\s*fl > 0 \? \{ limit: fl, period: document\.getElementById\('shop-freq-period'\)\.value \} : null/);
});

test('modal footer and frequency inputs stay within narrow edit sheets', () => {
    const source = read('public/css/partials/components.css');

    assert.match(source, /\.modal__content\s*\{[\s\S]*box-sizing:\s*border-box;/);
    assert.match(source, /\.modal__actions\s*\{[\s\S]*flex-wrap:\s*wrap;[\s\S]*width:\s*100%;/);
    assert.match(source, /\.modal__actions \.btn\s*\{[\s\S]*min-width:\s*0;/);
    assert.match(source, /\.input-group\s*\{[\s\S]*width:\s*100%;/);
    assert.match(source, /@media \(max-width: 768px\) \{[\s\S]*\.input-group\s*\{\s*flex-direction:\s*column;/);
});
