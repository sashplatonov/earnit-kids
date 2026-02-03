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

        // Insert balance
        await client.query(
            'INSERT INTO family_data (family_id, balance) VALUES ($1, $2) ON CONFLICT (family_id) DO UPDATE SET balance = $2',
            [dbId, data.balance || 0]
        );

        // Insert tasks
        if (data.tasks && Array.isArray(data.tasks)) {
            for (const task of data.tasks) {
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
            console.log(`    📝 Migrated ${data.tasks.length} tasks`);
        }

        // Insert shop items
        if (data.shop && Array.isArray(data.shop)) {
            for (const item of data.shop) {
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
            console.log(`    🛍️  Migrated ${data.shop.length} shop items`);
        }

        // Insert history
        if (data.history && Array.isArray(data.history)) {
            for (const entry of data.history) {
                const { type, timestamp, ...entryData } = entry;
                await client.query(
                    `INSERT INTO history (family_id, type, data, created_at)
                     VALUES ($1, $2, $3, $4)`,
                    [dbId, type || 'unknown', JSON.stringify(entryData), timestamp || new Date()]
                );
            }
            console.log(`    📜 Migrated ${data.history.length} history entries`);
        }

        // Insert requests
        if (data.requests && Array.isArray(data.requests)) {
            for (const req of data.requests) {
                const { id, type, status, created_at, ...reqData } = req;
                await client.query(
                    `INSERT INTO requests (family_id, type, data, status, created_at)
                     VALUES ($1, $2, $3, $4, $5)`,
                    [dbId, type || 'unknown', JSON.stringify(reqData), status || 'pending', created_at || new Date()]
                );
            }
            console.log(`    📨 Migrated ${data.requests.length} requests`);
        }

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

async function runDataMigration() {
    process.stdout.write('📊 Checking if data migration from JSON is needed...');

    // Check if families.json exists
    if (!fs.existsSync(FAMILIES_FILE)) {
        console.log(' ✅ (no JSON data to migrate)');
        return;
    }

    // Check if we already have data in DB. If we have families, we probably already migrated.
    try {
        const familiesResult = await query('SELECT COUNT(*) FROM families');
        if (parseInt(familiesResult.rows[0].count) > 0) {
            console.log(' ✅ (data already exists in DB)');
            return;
        }
    } catch (err) {
        console.log(' ❌ (error checking DB status)');
        throw err;
    }

    console.log('\n🚀 Starting data migration from JSON to PostgreSQL...');

    const client = await getClient();

    try {
        await client.query('BEGIN');

        // Load JSON data
        const familiesData = JSON.parse(fs.readFileSync(FAMILIES_FILE, 'utf8'));
        const families = familiesData.families || {};
        const familyIds = Object.keys(families);

        console.log(`📋 Found ${familyIds.length} families to migrate`);

        // Migrate super admin
        await migrateSuperAdmin(client, familiesData.super_admin);

        // Track family ID mapping for friends
        const familyIdMap = {};

        // Migrate families
        for (const familyId of familyIds) {
            const dbId = await migrateFamily(client, familyId, families[familyId]);
            if (dbId) {
                const friends = await migrateFamilyData(client, dbId, familyId);
                familyIdMap[familyId] = { dbId, friends };
            }
        }

        // Migrate friends
        await migrateFriends(client, familyIdMap);

        await client.query('COMMIT');

        console.log('\n✅ Data migration completed successfully!');

    } catch (err) {
        await client.query('ROLLBACK');
        console.error('\n❌ Data migration failed:', err.message);
        throw err;
    } finally {
        client.release();
    }
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
