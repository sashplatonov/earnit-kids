#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const ROOT = process.cwd();
const TARGETS = [
    path.join(ROOT, 'src'),
    path.join(ROOT, 'scripts'),
    path.join(ROOT, 'public', 'js'),
    path.join(ROOT, 'tests'),
    path.join(ROOT, 'test_version.js'),
];

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

function runSyntaxCheck(filePath) {
    const result = spawnSync(process.execPath, ['--check', filePath], {
        encoding: 'utf8',
    });

    if (result.status !== 0) {
        const output = (result.stderr || result.stdout || '').trim();
        return output || `Syntax check failed for ${filePath}`;
    }

    return null;
}

function main() {
    const files = [];
    for (const target of TARGETS) {
        collectJsFiles(target, files);
    }

    if (files.length === 0) {
        console.log('No JavaScript files found for syntax linting.');
        return;
    }

    const errors = [];
    for (const filePath of files.sort()) {
        const errorOutput = runSyntaxCheck(filePath);
        if (errorOutput) {
            errors.push({ filePath, errorOutput });
        }
    }

    if (errors.length > 0) {
        console.error(`Syntax lint failed (${errors.length} file(s)):`);
        for (const error of errors) {
            console.error(`\n[${error.filePath}]`);
            console.error(error.errorOutput);
        }
        process.exit(1);
    }

    console.log(`Syntax lint passed (${files.length} file(s)).`);
}

main();
