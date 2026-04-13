const fs = require('fs');
const path = require('path');

const ENV_FILE = path.join(__dirname, '..', '..', '..', '.env');

loadEnvFile(ENV_FILE);

const { Pool } = requirePg();

const jdbcConfig = parseJdbcUrl(process.env.DATABASE_URL);

const pool = new Pool({
    host: process.env.DB_HOST || jdbcConfig.host || 'localhost',
    port: toInteger(process.env.DB_PORT, jdbcConfig.port || 5432),
    database: process.env.DB_NAME || jdbcConfig.database || 'earnit_kids',
    user: process.env.DB_USER || jdbcConfig.user || 'postgres',
    password: process.env.DB_PASSWORD || jdbcConfig.password || 'postgres',
    ssl: resolveSsl(jdbcConfig.sslMode)
});

function loadEnvFile(filePath) {
    if (!fs.existsSync(filePath)) {
        return;
    }

    const lines = fs.readFileSync(filePath, 'utf8').split(/\r?\n/u);
    for (const rawLine of lines) {
        const line = rawLine.trim();
        if (!line || line.startsWith('#')) {
            continue;
        }

        const separatorIndex = line.indexOf('=');
        if (separatorIndex <= 0) {
            continue;
        }

        const key = line.slice(0, separatorIndex).trim();
        if (!key || process.env[key] !== undefined) {
            continue;
        }

        const rawValue = line.slice(separatorIndex + 1).trim();
        process.env[key] = stripWrappingQuotes(rawValue);
    }
}

function stripWrappingQuotes(value) {
    if (value.length < 2) {
        return value;
    }

    const first = value[0];
    const last = value[value.length - 1];
    if ((first === '"' && last === '"') || (first === '\'' && last === '\'')) {
        return value.slice(1, -1);
    }
    return value;
}

function requirePg() {
    const candidates = [
        'pg',
        path.join(__dirname, '..', '..', '..', 'web', 'node_modules', 'pg')
    ];

    for (const candidate of candidates) {
        try {
            return require(candidate);
        } catch (error) {
            if (error.code !== 'MODULE_NOT_FOUND') {
                throw error;
            }
        }
    }

    throw new Error(
        'Cannot resolve the "pg" package for backend/scripts. Run npm install in web/ or make pg resolvable.'
    );
}

function parseJdbcUrl(value) {
    if (!value || value.includes('${')) {
        return {};
    }

    const normalized = value.startsWith('jdbc:') ? value.slice(5) : value;
    let parsed;
    try {
        parsed = new URL(normalized);
    } catch {
        return {};
    }

    return {
        host: parsed.hostname || undefined,
        port: parsed.port ? Number(parsed.port) : undefined,
        database: parsed.pathname ? parsed.pathname.replace(/^\//u, '') : undefined,
        user: parsed.username ? decodeURIComponent(parsed.username) : undefined,
        password: parsed.password ? decodeURIComponent(parsed.password) : undefined,
        sslMode: parsed.searchParams.get('sslmode') || undefined
    };
}

function resolveSsl(sslMode) {
    return sslMode === 'require' ? { rejectUnauthorized: false } : undefined;
}

function toInteger(value, fallback) {
    const numeric = Number(value);
    return Number.isInteger(numeric) ? numeric : fallback;
}

function getDatabaseSchema() {
    const value = (process.env.DB_SCHEMA || 'public').trim();
    return value || 'public';
}

function quoteIdentifier(value) {
    return `"${String(value).replace(/"/gu, '""')}"`;
}

function getQualifiedTableName(tableName, schemaName = getDatabaseSchema()) {
    return `${quoteIdentifier(schemaName)}.${quoteIdentifier(tableName)}`;
}

function query(text, params = []) {
    return pool.query(text, params);
}

function getClient() {
    return pool.connect();
}

module.exports = {
    pool,
    query,
    getClient,
    getDatabaseSchema,
    quoteIdentifier,
    getQualifiedTableName
};
