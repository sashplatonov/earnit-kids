const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

function collectFiles(rootDir, predicate, result = []) {
    for (const entry of fs.readdirSync(rootDir, { withFileTypes: true })) {
        const fullPath = path.join(rootDir, entry.name);
        if (entry.isDirectory()) {
            collectFiles(fullPath, predicate, result);
            continue;
        }
        if (predicate(fullPath)) {
            result.push(fullPath);
        }
    }
    return result;
}

function normalizePath(rawValue) {
    if (!rawValue) return null;

    const apiIndex = rawValue.indexOf('/api/');
    const magicLinkIndex = rawValue.indexOf('/login-child/');
    const webSocketIndex = rawValue.indexOf('/ws');
    const startIndex = apiIndex >= 0
        ? apiIndex
        : magicLinkIndex >= 0
            ? magicLinkIndex
            : webSocketIndex;
    if (startIndex < 0) {
        return null;
    }

    const normalized = rawValue.slice(startIndex)
        .replace(/\$\{[^}]+\}/g, 'placeholder')
        .replace(/\?.*$/, '')
        .replace(/\/{2,}/g, '/')
        .replace(/\/$/, '');

    return normalized.endsWith('/placeholder')
        ? normalized
        : normalized.replace(/placeholder$/, '');
}

function extractFrontendRoutes(filePath) {
    const source = fs.readFileSync(filePath, 'utf8');
    const routes = [];
    let index = 0;

    while (index < source.length) {
        const candidates = [
            source.indexOf('/api/', index),
            source.indexOf('/login-child/', index),
            source.indexOf('/ws', index)
        ].filter(candidate => candidate >= 0);

        if (candidates.length === 0) {
            break;
        }

        const start = Math.min(...candidates);
        let end = start;
        while (end < source.length) {
            const char = source[end];
            if (char === '$' && source[end + 1] === '{') {
                const closingBrace = source.indexOf('}', end + 2);
                if (closingBrace < 0) {
                    break;
                }
                end = closingBrace + 1;
                continue;
            }
            if (/[\s"'`<>(){},;]/.test(char)) {
                break;
            }
            end += 1;
        }

        const normalized = normalizePath(source.slice(start, end));
        if (normalized) {
            routes.push(normalized);
        }

        index = end + 1;
    }

    return routes;
}

function extractBackendRoutes(filePath) {
    const source = fs.readFileSync(filePath, 'utf8');
    const routes = [];

    const classPathMatch = source.match(/@Path\("([^"]+)"\)[\s\S]*?public class/);
    if (classPathMatch) {
        const classPath = classPathMatch[1];
        const methodPattern = /@(GET|POST|PUT|DELETE|PATCH)([\s\S]*?)public\s+Response\s+\w+\s*\(/g;
        let match;
        while ((match = methodPattern.exec(source))) {
            const methodBlock = match[2];
            const methodPathMatch = methodBlock.match(/@Path\("([^"]*)"\)/);
            routes.push((classPath + (methodPathMatch ? methodPathMatch[1] : '')).replace(/\/$/, ''));
        }
    }

    const webSocketMatch = source.match(/@WebSocket\s*\(\s*path\s*=\s*"([^"]+)"\s*\)/);
    if (webSocketMatch) {
        routes.push(webSocketMatch[1].replace(/\/$/, ''));
    }

    return routes;
}

test('frontend REST routes resolve to backend resources', () => {
    const repoRoot = path.join(__dirname, '../../');
    const frontendFiles = [
        ...collectFiles(path.join(repoRoot, 'public/js'), file => file.endsWith('.js')),
        ...collectFiles(path.join(repoRoot, 'views'), file => file.endsWith('.html'))
    ];
    const backendFiles = collectFiles(
        path.join(repoRoot, '../backend/src/main/java/com/sashplatonov/earnit/kids/resource'),
        file => file.endsWith('.java')
    );

    const frontendRoutes = new Map();
    for (const filePath of frontendFiles) {
        for (const route of extractFrontendRoutes(filePath)) {
            const existing = frontendRoutes.get(route) || [];
            existing.push(path.relative(repoRoot, filePath));
            frontendRoutes.set(route, existing);
        }
    }

    const backendRoutes = new Set(['/api/client-errors', '/api/openapi.yaml']);
    for (const filePath of backendFiles) {
        for (const route of extractBackendRoutes(filePath)) {
            backendRoutes.add(route);
        }
    }

    const matchesBackendRoute = (frontendRoute, backendRoute) => {
        const routePattern = '^' + backendRoute
            .replace(/\{[^}]*\}/g, '[^/]+')
            .replace(/\//g, '\\/') + '$';
        return new RegExp(routePattern).test(frontendRoute);
    };

    const missingRoutes = [...frontendRoutes.entries()]
        .filter(([route]) => ![...backendRoutes].some((backendRoute) => matchesBackendRoute(route, backendRoute)))
        .map(([route, files]) => `${route} <- ${files.join(', ')}`);

    assert.deepEqual(missingRoutes, []);
});
