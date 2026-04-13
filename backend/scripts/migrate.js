#!/usr/bin/env node

/**
 * Database Migration Script
 * 
 * Usage:
 *   npm run migrate          - Run all pending migrations
 *   npm run migrate status   - Show migration status
 */

require('dotenv').config();

const fs = require('fs');
const path = require('path');
const { pool, query } = require('../src/db/connection');
const { getDatabaseSchema, quoteIdentifier } = require('../src/db/schema');

const MIGRATIONS_DIR = path.join(__dirname, '../migrations');
const PROJECT_SCHEMA = getDatabaseSchema();
const PROJECT_SCHEMA_SQL = quoteIdentifier(PROJECT_SCHEMA);
const MIGRATIONS_TABLE = 'migrations';
const MIGRATIONS_TABLE_SQL = `${PROJECT_SCHEMA_SQL}.${quoteIdentifier(MIGRATIONS_TABLE)}`;
const schemaQualifiedMigrations = `${PROJECT_SCHEMA}.${MIGRATIONS_TABLE}`;
const publicQualifiedMigrations = `public.${MIGRATIONS_TABLE}`;

async function tableExists(qualifiedName) {
    const result = await pool.query(
        'SELECT to_regclass($1) IS NOT NULL AS exists',
        [qualifiedName]
    );
    return result.rows[0]?.exists ?? false;
}

async function ensureMigrationsTable() {
    await pool.query(`CREATE SCHEMA IF NOT EXISTS ${PROJECT_SCHEMA_SQL}`);

    const publicTableExists = await tableExists(publicQualifiedMigrations);
    const schemaTableExists = await tableExists(schemaQualifiedMigrations);

    const sql = `
        CREATE TABLE IF NOT EXISTS ${MIGRATIONS_TABLE_SQL} (
            id SERIAL PRIMARY KEY,
            name VARCHAR(255) NOT NULL UNIQUE,
            executed_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
        );
        CREATE INDEX IF NOT EXISTS idx_migrations_name ON ${MIGRATIONS_TABLE_SQL}(name);
    `;
    await pool.query(sql);

    if (publicTableExists) {
        await pool.query(`
            INSERT INTO ${MIGRATIONS_TABLE_SQL} (id, name, executed_at)
            SELECT id, name, executed_at FROM public.${quoteIdentifier(MIGRATIONS_TABLE)}
            ON CONFLICT (name) DO NOTHING
        `);

        if (!schemaTableExists) {
            await pool.query(
                `DROP TABLE IF EXISTS public.${quoteIdentifier(MIGRATIONS_TABLE)}`
            );
        }
    }
}

async function getExecutedMigrations() {
    try {
        const result = await query(`SELECT name FROM ${MIGRATIONS_TABLE_SQL} ORDER BY name`);
        return result.rows.map(row => row.name);
    } catch (err) {
        return [];
    }
}

async function getSchemaSignals() {
    const [tablesResult, columnsResult, functionsResult] = await Promise.all([
        pool.query(
            `SELECT table_name FROM information_schema.tables WHERE table_schema = $1`,
            [PROJECT_SCHEMA]
        ),
        pool.query(
            `SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = $1`,
            [PROJECT_SCHEMA]
        ),
        pool.query(
            `
                SELECT p.proname
                FROM pg_proc p
                JOIN pg_namespace n ON n.oid = p.pronamespace
                WHERE n.nspname = $1
            `,
            [PROJECT_SCHEMA]
        )
    ]);

    return {
        tables: new Set(tablesResult.rows.map(row => row.table_name)),
        columns: new Set(columnsResult.rows.map(row => `${row.table_name}.${row.column_name}`)),
        functions: new Set(functionsResult.rows.map(row => row.proname))
    };
}

function isExistingSchemaBaselineReady(signals = {}) {
    const tables = signals.tables instanceof Set ? signals.tables : new Set();
    const columns = signals.columns instanceof Set ? signals.columns : new Set();
    const functions = signals.functions instanceof Set ? signals.functions : new Set();
    const hasTable = (tableName) => tables.has(tableName);
    const hasColumn = (tableName, columnName) => columns.has(`${tableName}.${columnName}`);

    return [
        hasTable('families'),
        hasTable('children'),
        hasTable('tasks'),
        hasTable('shop_items'),
        hasTable('requests'),
        hasTable('device_push_tokens'),
        !hasTable('family_data'),
        hasColumn('tasks', 'child_id'),
        hasColumn('shop_items', 'child_id'),
        hasColumn('requests', 'request_type'),
        hasColumn('requests', 'item_id'),
        hasColumn('requests', 'money_amount'),
        hasColumn('children', 'daily_coin_limit'),
        hasColumn('children', 'theme'),
        hasColumn('families', 'last_selected_child_id'),
        hasColumn('families', 'reset_token'),
        hasColumn('families', 'updated_at'),
        !hasColumn('families', 'child_token'),
        hasColumn('device_push_tokens', 'endpoint'),
        hasColumn('device_push_tokens', 'push_type'),
        functions.has('update_updated_at_column')
    ].every(Boolean);
}

async function maybeBaselineExistingSchema(files, executed) {
    if (executed.length > 0 || files.length === 0) {
        return executed;
    }

    const signals = await getSchemaSignals();
    if (!isExistingSchemaBaselineReady(signals)) {
        return executed;
    }

    const client = await pool.connect();
    try {
        await client.query('BEGIN');
        for (const file of files) {
            await client.query(
                `INSERT INTO ${MIGRATIONS_TABLE_SQL} (name) VALUES ($1) ON CONFLICT (name) DO NOTHING`,
                [file]
            );
        }
        await client.query('COMMIT');
    } catch (err) {
        await client.query('ROLLBACK');
        throw err;
    } finally {
        client.release();
    }

    console.log(`\n🧭 Detected an existing ${PROJECT_SCHEMA} schema; baselining ${files.length} migration(s).`);
    return files;
}

function getMigrationFiles() {
    if (!fs.existsSync(MIGRATIONS_DIR)) {
        throw new Error(`Migrations directory not found: ${MIGRATIONS_DIR}`);
    }

    return fs.readdirSync(MIGRATIONS_DIR)
        .filter(f => f.endsWith('.sql'))
        .sort();
}

async function runMigration(filename) {
    const filePath = path.join(MIGRATIONS_DIR, filename);
    const sql = fs.readFileSync(filePath, 'utf8').replaceAll('__DB_SCHEMA__', PROJECT_SCHEMA_SQL);

    const client = await pool.connect();
    try {
        await client.query('BEGIN');

        // Execute migration SQL
        await client.query(sql);

        // Record migration as executed
        await client.query(
            `INSERT INTO ${MIGRATIONS_TABLE_SQL} (name) VALUES ($1) ON CONFLICT (name) DO NOTHING`,
            [filename]
        );

        await client.query('COMMIT');
        return true;
    } catch (err) {
        await client.query('ROLLBACK');
        throw err;
    } finally {
        client.release();
    }
}

async function testConnection() {
    try {
        await pool.query('SELECT NOW()');
    } catch (err) {
        console.error(' ❌\n❌ Database connection failed:', err.message);
        throw new Error('Database connection failed');
    }
}

async function getPendingMigrations() {
    await ensureMigrationsTable();
    const files = getMigrationFiles();
    const executed = await getExecutedMigrations();
    const effectiveExecuted = await maybeBaselineExistingSchema(files, executed);
    return files.filter(f => !effectiveExecuted.includes(f));
}

async function migrate() {
    process.stdout.write('🚀 Checking database migrations...');
    await testConnection();

    const pending = await getPendingMigrations();
    if (pending.length === 0) {
        console.log(' ✅ (up to date)');
        return;
    }

    console.log(`\n📋 Found ${pending.length} pending migration(s):`);

    for (const file of pending) {
        process.stdout.write(`  ⏳ Running ${file}...`);
        try {
            await runMigration(file);
            console.log(' ✅');
        } catch (err) {
            console.log(' ❌');
            console.error(`\n❌ Migration failed: ${err.message}`);
            throw err;
        }
    }

    console.log(`✅ Successfully ran ${pending.length} migration(s)\n`);
}

async function status() {
    console.log('📊 Migration Status\n');

    try {
        await pool.query('SELECT NOW()');
    } catch (err) {
        console.error('❌ Database connection failed:', err.message);
        throw err;
    }

    const executed = await getExecutedMigrations();
    const files = getMigrationFiles();

    console.log('Migrations:');
    for (const file of files) {
        const isExecuted = executed.includes(file);
        const status = isExecuted ? '✅' : '⏳';
        console.log(`  ${status} ${file}`);
    }

    const pending = files.filter(f => !executed.includes(f));
    console.log(`\nExecuted: ${executed.length}, Pending: ${pending.length}`);
}

async function main() {
    const command = process.argv[2] || 'up';

    try {
        switch (command) {
            case 'up':
            case 'migrate':
                await migrate();
                break;
            case 'status':
                await status();
                break;
            default:
                console.log('Usage: npm run migrate [up|status]');
        }
    } catch (err) {
        console.error('❌ Error:', err.message);
        process.exit(1);
    } finally {
        await pool.end();
    }
}

if (require.main === module) {
    main();
}

module.exports = { migrate, status, isExistingSchemaBaselineReady };
