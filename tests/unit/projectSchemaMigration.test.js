const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

const migrationPath = path.join(__dirname, '../../migrations/015_project_schema.sql');
const migrationSql = fs.readFileSync(migrationPath, 'utf8');

test('project schema migration handles duplicate restored tables', () => {
    assert.match(migrationSql, /DROP TABLE __DB_SCHEMA__\.\%I CASCADE/);
    assert.match(migrationSql, /ALTER TABLE public\.\%I SET SCHEMA __DB_SCHEMA__/);
});

test('project schema migration replaces duplicate trigger function before move', () => {
    assert.match(migrationSql, /DROP FUNCTION __DB_SCHEMA__\.update_updated_at_column\(\) CASCADE/);
    assert.match(migrationSql, /ALTER FUNCTION public\.update_updated_at_column\(\) SET SCHEMA __DB_SCHEMA__/);
});
