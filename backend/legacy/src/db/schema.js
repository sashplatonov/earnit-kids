/** @file Database schema helpers */
const packageJson = require('../../package.json');

function normalizeSchemaName(value) {
    const normalized = String(value || '')
        .trim()
        .toLowerCase()
        .replace(/[^a-z0-9_]+/g, '_')
        .replace(/^_+|_+$/g, '');

    if (!normalized) {
        return 'public';
    }

    return /^[0-9]/.test(normalized) ? `_${normalized}` : normalized;
}

function getDatabaseSchema() {
    return normalizeSchemaName(process.env.DB_SCHEMA || packageJson.name);
}

function getSearchPath() {
    return `${getDatabaseSchema()},public`;
}

function quoteIdentifier(identifier) {
    return `"${String(identifier).replace(/"/g, '""')}"`;
}

module.exports = {
    getDatabaseSchema,
    getSearchPath,
    normalizeSchemaName,
    quoteIdentifier
};
