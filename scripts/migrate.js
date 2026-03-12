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

async function ensureMigrationsTable() {
    await pool.query(`CREATE SCHEMA IF NOT EXISTS ${PROJECT_SCHEMA_SQL}`);

    const publicLookup = await pool.query(
        'SELECT to_regclass($1) IS NOT NULL AS exists',
        [publicQualifiedMigrations]
    );
    const schemaLookup = await pool.query(
        'SELECT to_regclass($1) IS NOT NULL AS exists',
        [schemaQualifiedMigrations]
    );

    const migrationsInPublic = publicLookup.rows[0]?.exists ?? false;
    const migrationsInSchema = schemaLookup.rows[0]?.exists ?? false;

    if (migrationsInPublic && !migrationsInSchema) {
        await pool.query(
            `ALTER TABLE public.${quoteIdentifier(MIGRATIONS_TABLE)} SET SCHEMA ${PROJECT_SCHEMA_SQL}`
        );
    }

    const sql = `
        CREATE TABLE IF NOT EXISTS ${MIGRATIONS_TABLE_SQL} (
            id SERIAL PRIMARY KEY,
            name VARCHAR(255) NOT NULL UNIQUE,
            executed_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
        );
        CREATE INDEX IF NOT EXISTS idx_migrations_name ON ${MIGRATIONS_TABLE_SQL}(name);
    `;
    await pool.query(sql);
}

async function getExecutedMigrations() {
    try {
        const result = await query(`SELECT name FROM ${MIGRATIONS_TABLE_SQL} ORDER BY name`);
        return result.rows.map(row => row.name);
    } catch (err) {
        return [];
    }
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
    const executed = await getExecutedMigrations();
    const files = getMigrationFiles();
    return files.filter(f => !executed.includes(f));
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

module.exports = { migrate, status };
