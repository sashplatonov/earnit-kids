#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

const ROOT = process.cwd();
const TARGETS = [
    path.join(ROOT, 'src'),
    path.join(ROOT, 'scripts'),
    path.join(ROOT, 'test_version.js'),
];

const IMPORT_PATTERN = /^\s*import\s.+from\s+['"][^'"]+['"]\s*;?\s*$/m;
const EXPORT_PATTERN = /^\s*export\s/m;

function collectJsFiles(targetPath, files) {
    if (!fs.existsSync(targetPath)) {
        return;
    }

    const stat = fs.statSync(targetPath);
    if (stat.isFile()) {
        if (targetPath.endsWith('.js')) {
            files.push(targetPath);
        }
        return;
    }

    const entries = fs.readdirSync(targetPath, { withFileTypes: true });
    for (const entry of entries) {
        const fullPath = path.join(targetPath, entry.name);
        if (entry.isDirectory()) {
            collectJsFiles(fullPath, files);
            continue;
        }
        if (entry.isFile() && fullPath.endsWith('.js')) {
            files.push(fullPath);
        }
    }
}

function lintFile(filePath) {
    const source = fs.readFileSync(filePath, 'utf8');
    const errors = [];

    if (IMPORT_PATTERN.test(source)) {
        errors.push('ESM import detected');
    }

    if (EXPORT_PATTERN.test(source)) {
        errors.push('ESM export detected');
    }

    return errors;
}

function main() {
    const files = [];
    for (const target of TARGETS) {
        collectJsFiles(target, files);
    }

    const violations = [];
    for (const filePath of files.sort()) {
        const errors = lintFile(filePath);
        if (errors.length > 0) {
            violations.push({ filePath, errors });
        }
    }

    if (violations.length > 0) {
        console.error(`CommonJS lint failed (${violations.length} file(s)):`);
        for (const violation of violations) {
            console.error(`- ${violation.filePath}: ${violation.errors.join(', ')}`);
        }
        process.exit(1);
    }

    console.log(`CommonJS lint passed (${files.length} file(s)).`);
}

main();
