const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const {
    normalizeStaticPath
} = require('../../src/controllers/staticUtils');

const VIEWS_DIR = path.join(__dirname, '../../views');
const PUBLIC_DIR = path.join(__dirname, '../../public');
const DIST_DIR = path.join(PUBLIC_DIR, 'dist');

const STYLE_LINK_REGEX = /<link\b[^>]*rel=["']stylesheet["'][^>]*href=["']([^"']+)["'][^>]*>/gi;

function collectHtmlFiles(dir) {
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    const files = [];

    for (const entry of entries) {
        const entryPath = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            files.push(...collectHtmlFiles(entryPath));
        } else if (entry.isFile() && entry.name.endsWith('.html')) {
            files.push(entryPath);
        }
    }

    return files;
}

function extractStylesFromFile(filePath) {
    const contents = fs.readFileSync(filePath, 'utf8');
    const matches = [];
    let match;

    while ((match = STYLE_LINK_REGEX.exec(contents)) !== null) {
        matches.push(match[1]);
    }

    return matches;
}

function resolveCssAssetPath(rawHref) {
    if (!rawHref) return null;

    const [withoutQuery] = rawHref.split('?');
    const trimmed = withoutQuery.trim();
    if (!trimmed) return null;
    if (/^https?:\/\//i.test(trimmed) || trimmed.startsWith('//')) {
        return null;
    }

    const normalized = normalizeStaticPath(trimmed);
    if (!normalized) return null;

    const cleaned = normalized.replace(/^\/+/, '');
    if (!cleaned || !cleaned.endsWith('.css')) return null;

    return cleaned;
}

function resolveAssetFullPath(assetPath) {
    const baseDir = process.env.NODE_ENV === 'production' ? DIST_DIR : PUBLIC_DIR;
    if (!fs.existsSync(baseDir)) {
        throw new Error(`Asset base directory missing: ${baseDir}`);
    }
    return path.join(baseDir, assetPath);
}

test('All view templates reference existing stylesheet assets', () => {
    const htmlFiles = collectHtmlFiles(VIEWS_DIR);
    assert.ok(htmlFiles.length, `No view templates found under ${VIEWS_DIR}`);

    const stylesheetPaths = new Set();

    for (const filePath of htmlFiles) {
        for (const href of extractStylesFromFile(filePath)) {
            const asset = resolveCssAssetPath(href);
            if (asset) {
                stylesheetPaths.add(asset);
            }
        }
    }

    assert.ok(stylesheetPaths.size, 'No stylesheet links were discovered in the view templates.');

    const missingAssets = [];

    for (const assetPath of stylesheetPaths) {
        const fullPath = resolveAssetFullPath(assetPath);
        if (!fs.existsSync(fullPath)) {
            missingAssets.push(`${assetPath} -> ${fullPath} (missing)`);
            continue;
        }

        const stats = fs.statSync(fullPath);
        if (stats.size === 0) {
            missingAssets.push(`${assetPath} -> ${fullPath} (empty file)`);
        }
    }

    assert.strictEqual(missingAssets.length, 0, `Missing or empty stylesheet assets:\n${missingAssets.join('\n')}`);
});
