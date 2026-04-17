const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

function read(relativePath) {
    return fs.readFileSync(path.join(__dirname, '../../', relativePath), 'utf8');
}

test('header profile and child nickname use readable light-theme colors', () => {
    const source = read('public/css/partials/layout.css');

    assert.match(source, /\.header__profile\s*\{[\s\S]*color:\s*var\(--color-text-high-contrast\);/);
    assert.match(source, /\.header__profile\s*\{[\s\S]*background:\s*rgba\(255,\s*255,\s*255,\s*0\.88\);/);
    assert.match(source, /\.header__profile-label,\s*\.header__child-nickname\s*\{[\s\S]*color:\s*var\(--color-text-high-contrast\);/);
});

test('child switcher avoids hardcoded white text in light theme', () => {
    const source = read('public/js/modules/child-switcher-ui.js');

    assert.match(source, /\.child-menu-btn\s*\{[\s\S]*color:\s*var\(--color-text-high-contrast\);/);
    assert.match(source, /\.child-menu-item\s*\{[\s\S]*color:\s*var\(--color-text-high-contrast\);/);
    assert.doesNotMatch(source, /\.child-menu-btn\s*\{[\s\S]*color:\s*white;/);
});
