/** @file CLI helper — add concise JSDoc `@file` headers to source files. */
const fs = require('fs').promises;
const path = require('path');

const TARGET_DIRS = ['src', path.join('public', 'js')];
const EXTENSIONS = new Set(['.js', '.cjs']);
const MAX_DESCRIPTION_LENGTH = 98;
const repoRoot = path.resolve(__dirname, '..');

const AREA_DESCRIPTIONS = [
    { pattern: '/src/api/', desc: 'REST API handlers' },
    { pattern: '/src/controllers/', desc: 'REST controller helpers' },
    { pattern: '/src/db/', desc: 'PostgreSQL data access' },
    { pattern: '/src/middleware/', desc: 'Express middleware' },
    { pattern: '/src/routes/', desc: 'Express route definitions' },
    { pattern: '/src/services/', desc: 'business services' },
    { pattern: '/src/config/', desc: 'configuration helpers' },
    { pattern: '/src/templates/', desc: 'server template helpers' },
    { pattern: '/src/utils/', desc: 'utility helpers' },
    { pattern: '/public/js/modules/', desc: 'frontend UI module' },
    { pattern: '/public/js/', desc: 'frontend helper script' }
];

async function main() {
    const files = await gatherTargetFiles();
    const changed = await applyHeaders(files);
    reportResults(changed);
}

async function gatherTargetFiles() {
    const accumulator = [];

    for (const dir of TARGET_DIRS) {
        const absoluteDir = path.join(repoRoot, dir);
        const stats = await fs.stat(absoluteDir).catch(() => null);
        if (!stats || !stats.isDirectory()) {
            continue;
        }

        await collectFiles(absoluteDir, accumulator);
    }

    return accumulator;
}

async function applyHeaders(files) {
    const changed = [];

    for (const filePath of files) {
        const content = await fs.readFile(filePath, 'utf8');
        if (hasHeader(content)) {
            continue;
        }

        const description = buildDescription(filePath);
        const header = `/** @file ${description} */\n`;
        const updated = insertHeader(content, header);
        await fs.writeFile(filePath, updated);
        changed.push(path.relative(repoRoot, filePath));
    }

    return changed;
}

function reportResults(changed) {
    if (changed.length) {
        console.log(`🪙 Добавлены заголовки @file в ${changed.length} файлах:`);
        changed.forEach((file) => console.log(`  🪙 ${file}`));
    } else {
        console.log('🪙 Все файлы уже содержат заголовки @file.');
    }
}

async function collectFiles(dir, accumulator) {
    const entries = await fs.readdir(dir, { withFileTypes: true });

    for (const entry of entries) {
        const entryPath = path.join(dir, entry.name);

        if (entry.isDirectory()) {
            await collectFiles(entryPath, accumulator);
            continue;
        }

        if (!EXTENSIONS.has(path.extname(entry.name))) {
            continue;
        }

        accumulator.push(entryPath);
    }
}

function hasHeader(content) {
    const headChunk = content.slice(0, 400);
    return /\@file/.test(headChunk);
}

function buildDescription(filePath) {
    const normalized = filePath.split(path.sep).join('/');
    let area = 'server logic';

    for (const entry of AREA_DESCRIPTIONS) {
        if (normalized.includes(entry.pattern)) {
            area = entry.desc;
            break;
        }
    }

    const baseName = path.basename(filePath, path.extname(filePath));
    const human = humanizeBaseName(baseName);
    let description = human ? `${human} ${area}` : area;
    if (description.length > MAX_DESCRIPTION_LENGTH) {
        description = truncateWords(description, MAX_DESCRIPTION_LENGTH);
    }

    return description;
}

function humanizeBaseName(name) {
    const withoutExt = name.replace(/\.[^/.]+$/, '');
    const spaced = withoutExt
        .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
        .replace(/[-_.]+/g, ' ')
        .trim();
    if (!spaced) {
        return '';
    }

    return spaced
        .toLowerCase()
        .replace(/\b\w/g, (character) => character.toUpperCase());
}

function truncateWords(text, limit) {
    if (text.length <= limit) {
        return text;
    }

    const words = text.split(' ');
    let truncated = '';

    for (const word of words) {
        if (!word) {
            continue;
        }

        const candidate = truncated ? `${truncated} ${word}` : word;
        if (candidate.length > limit) {
            break;
        }

        truncated = candidate;
    }

    return truncated || text.slice(0, limit).trim();
}

function insertHeader(content, header) {
    if (content.startsWith('#!')) {
        const newlineIndex = content.indexOf('\n');
        if (newlineIndex === -1) {
            return `${content}\n${header}`;
        }

        const prefix = content.slice(0, newlineIndex + 1);
        const suffix = content.slice(newlineIndex + 1);
        return `${prefix}${header}${suffix}`;
    }

    return `${header}${content}`;
}

main().catch((error) => {
    console.error('❌ Ошибка при генерации заголовков @file:', error);
    process.exit(1);
});
