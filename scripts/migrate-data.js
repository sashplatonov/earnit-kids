#!/usr/bin/env node

/**
 * Data Migration Script
 * Migrates existing JSON data to PostgreSQL
 * 
 * Usage: npm run migrate:data
 */

require('dotenv').config();

const fs = require('fs');
const path = require('path');
const { pool, query, getClient } = require('../src/db/connection');

const DATA_DIR = path.join(__dirname, '../data');
const FAMILIES_FILE = path.join(DATA_DIR, 'families.json');
const FAMILIES_DATA_DIR = path.join(DATA_DIR, 'families');

async function migrateSuperAdmin(client, superAdmin) {
    if (!superAdmin) {
        console.log('  ⏭️  No super_admin to migrate');
        return;
    }

    // Use env vars if available, otherwise use JSON data
    const email = process.env.SUPER_ADMIN_EMAIL || superAdmin.email;
    const password = process.env.SUPER_ADMIN_PASSWORD || superAdmin.password;

    await client.query(
        `INSERT INTO super_admin (email, password)
         VALUES ($1, $2)
         ON CONFLICT (email) DO UPDATE SET password = $2`,
        [email, password]
    );
    console.log('  ✅ Migrated super_admin');
}

async function migrateFamily(client, familyId, familyData) {
    // Check if family already exists
    const existing = await client.query(
        'SELECT id FROM families WHERE family_id = $1',
        [familyId]
    );

    if (existing.rows.length > 0) {
        console.log(`  ⏭️  Family ${familyId} already exists, skipping...`);
        return existing.rows[0].id;
    }

    // Insert family
    const result = await client.query(
        `INSERT INTO families (family_id, name, email, admin_password, child_token, monthly_limit, child_nickname, is_blocked, created_at, last_activity)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
         RETURNING id`,
        [
            familyId,
            familyData.name || 'Shop',
            familyData.email,
            familyData.admin_password,
            familyData.child_token || null,
            familyData.monthly_limit || 10000,
            familyData.child_nickname || '',
            familyData.isBlocked || false,
            familyData.created_at || new Date().toISOString(),
            familyData.last_activity || new Date().toISOString()
        ]
    );

    const dbId = result.rows[0].id;
    console.log(`  ✅ Migrated family: ${familyData.name} (${familyId})`);
    return dbId;
}

async function upsertFamilyBalance(client, dbId, balance) {
    await client.query(
        'INSERT INTO family_data (family_id, balance) VALUES ($1, $2) ON CONFLICT (family_id) DO UPDATE SET balance = $2',
        [dbId, balance || 0]
    );
}

async function migrateTasks(client, dbId, tasks) {
    if (!Array.isArray(tasks)) return;

    for (const task of tasks) {
        await client.query(
            `INSERT INTO tasks (family_id, task_id, name, coins, group_name, frequency, comment, money_limit)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
             ON CONFLICT (family_id, task_id) DO NOTHING`,
            [
                dbId,
                task.id,
                task.name,
                task.coins || 0,
                task.group || null,
                task.frequency ? JSON.stringify(task.frequency) : null,
                task.comment || null,
                task.money_limit || null
            ]
        );
    }
    console.log(`    📝 Migrated ${tasks.length} tasks`);
}

async function migrateShopItems(client, dbId, shopItems) {
    if (!Array.isArray(shopItems)) return;

    for (const item of shopItems) {
        await client.query(
            `INSERT INTO shop_items (family_id, item_id, name, price, group_name, frequency, money_limit)
             VALUES ($1, $2, $3, $4, $5, $6, $7)
             ON CONFLICT (family_id, item_id) DO NOTHING`,
            [
                dbId,
                item.id,
                item.name,
                item.price || 0,
                item.group || null,
                item.frequency ? JSON.stringify(item.frequency) : null,
                item.money_limit || null
            ]
        );
    }
    console.log(`    🛍️  Migrated ${shopItems.length} shop items`);
}

function resolveRelatedId(entry) {
    return entry.itemId || entry.taskId || entry.relatedId || null;
}

async function insertHistoryEntry(client, dbId, entry) {
    const { type, timestamp, id, amount, description, moneyAmount } = entry;
    const finalRelatedId = resolveRelatedId(entry);
    await client.query(
        `INSERT INTO history (family_id, external_id, type, amount, description, money_amount, related_id, created_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
        [
            dbId,
            id || null,
            type || 'unknown',
            amount || 0,
            description || '',
            moneyAmount || 0,
            finalRelatedId,
            timestamp || entry.date || new Date()
        ]
    );
}

async function migrateHistory(client, dbId, history) {
    if (!Array.isArray(history)) return;

    for (const entry of history) {
        await insertHistoryEntry(client, dbId, entry);
    }
    console.log(`    📜 Migrated ${history.length} history entries`);
}

async function migrateRequests(client, dbId, requests) {
    if (!Array.isArray(requests)) return;

    for (const req of requests) {
        const { id, status, created_at, taskId, taskName, coins, date } = req;
        await client.query(
            `INSERT INTO requests (family_id, external_id, task_id, task_name, coins, status, created_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7)`,
            [
                dbId,
                id || null,
                taskId || null,
                taskName || '',
                coins || 0,
                status || 'pending',
                created_at || date || new Date()
            ]
        );
    }
    console.log(`    📨 Migrated ${requests.length} requests`);
}

async function migrateFamilyData(client, dbId, familyId) {
    const familyFile = path.join(FAMILIES_DATA_DIR, `${familyId}.json`);

    if (!fs.existsSync(familyFile)) {
        console.log(`  ⏭️  No data file for ${familyId}`);
        // Create default family_data record
        await client.query(
            'INSERT INTO family_data (family_id, balance) VALUES ($1, $2) ON CONFLICT DO NOTHING',
            [dbId, 0]
        );
        return;
    }

    try {
        const data = JSON.parse(fs.readFileSync(familyFile, 'utf8'));
        await upsertFamilyBalance(client, dbId, data.balance);
        await migrateTasks(client, dbId, data.tasks);
        await migrateShopItems(client, dbId, data.shop);
        await migrateHistory(client, dbId, data.history);
        await migrateRequests(client, dbId, data.requests);

        // Friends will be migrated in a second pass (need all families first)
        return data.friends || [];

    } catch (err) {
        console.error(`  ❌ Error migrating data for ${familyId}:`, err.message);
        return [];
    }
}

async function migrateFriends(client, familyIdMap) {
    console.log('\n🔗 Migrating friend relationships...');

    for (const [familyId, { dbId, friends }] of Object.entries(familyIdMap)) {
        if (!friends || friends.length === 0) continue;

        for (const friendFamilyId of friends) {
            const friendInfo = familyIdMap[friendFamilyId];
            if (!friendInfo) {
                console.log(`  ⚠️  Friend ${friendFamilyId} not found for ${familyId}`);
                continue;
            }

            try {
                await client.query(
                    `INSERT INTO friends (family_id, friend_family_id)
                     VALUES ($1, $2) ON CONFLICT DO NOTHING`,
                    [dbId, friendInfo.dbId]
                );
            } catch (err) {
                console.error(`  ❌ Error adding friend ${friendFamilyId} for ${familyId}:`, err.message);
            }
        }
    }
    console.log('  ✅ Friend relationships migrated');
}

async function shouldSkipMigration() {
    process.stdout.write('📊 Checking if data migration from JSON is needed...');

    if (!fs.existsSync(FAMILIES_FILE)) {
        console.log(' ✅ (no JSON data to migrate)');
        return true;
    }

    try {
        const familiesRes = await query('SELECT COUNT(*) FROM families');
        if (parseInt(familiesRes.rows[0].count) > 0) {
            console.log(' ✅ (data already exists in DB)');
            return true;
        }
    } catch (err) {
        console.log(' ❌ (error checking DB status)');
        throw err;
    }
    return false;
}

async function loadMigrationData() {
    const familiesData = JSON.parse(fs.readFileSync(FAMILIES_FILE, 'utf8'));
    const families = familiesData.families || {};
    return {
        superAdmin: familiesData.super_admin,
        families,
        familyIds: Object.keys(families)
    };
}

async function migrateAllFamilies(client, familyIds, families) {
    const familyIdMap = {};
    for (const familyId of familyIds) {
        const dbId = await migrateFamily(client, familyId, families[familyId]);
        if (dbId) {
            const friends = await migrateFamilyData(client, dbId, familyId);
            familyIdMap[familyId] = { dbId, friends };
        }
    }
    return familyIdMap;
}

async function runDataMigration() {
    if (await shouldSkipMigration()) return;

    console.log('\n🚀 Starting data migration from JSON to PostgreSQL...');
    const client = await getClient();
    try {
        await client.query('BEGIN');
        const { superAdmin, families, familyIds } = await loadMigrationData();
        console.log(`📋 Found ${familyIds.length} families to migrate`);

        await migrateSuperAdmin(client, superAdmin);
        const familyIdMap = await migrateAllFamilies(client, familyIds, families);

        await migrateFriends(client, familyIdMap);
        await client.query('COMMIT');
        console.log('\n✅ Data migration completed successfully!');
    } catch (err) {
        await client.query('ROLLBACK');
        console.error('\n❌ Data migration failed:', err.message);
        throw err;
    } finally { client.release(); }
}

if (require.main === module) {
    runDataMigration()
        .then(() => pool.end())
        .catch(err => {
            console.error(err);
            process.exit(1);
        });
}

module.exports = { runDataMigration };
