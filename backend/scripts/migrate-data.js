#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { pool, getClient, getQualifiedTableName } = require('./lib/db');
const { buildLegacyFamilySnapshot } = require('./lib/legacyFamilyData');

const DATA_DIR = path.join(__dirname, '../data');
const FAMILIES_FILE = path.join(DATA_DIR, 'families.json');
const FAMILIES_DATA_DIR = path.join(DATA_DIR, 'families');

const TABLES = {
    superAdmin: getQualifiedTableName('super_admin'),
    families: getQualifiedTableName('families'),
    children: getQualifiedTableName('children'),
    tasks: getQualifiedTableName('tasks'),
    shopItems: getQualifiedTableName('shop_items'),
    history: getQualifiedTableName('history'),
    requests: getQualifiedTableName('requests'),
    friends: getQualifiedTableName('friends')
};

async function migrateSuperAdmin(client, superAdmin) {
    if (!superAdmin) {
        console.log('  ⏭️  No super_admin to migrate');
        return;
    }

    const email = process.env.SUPER_ADMIN_EMAIL || superAdmin.email;
    const password = process.env.SUPER_ADMIN_PASSWORD || superAdmin.password;
    if (!email || !password) {
        console.log('  ⏭️  No super_admin credentials to migrate');
        return;
    }

    await client.query(
        `INSERT INTO ${TABLES.superAdmin} (email, password)
         VALUES ($1, $2)
         ON CONFLICT (email) DO UPDATE SET password = EXCLUDED.password`,
        [email, password]
    );
    console.log('  ✅ Migrated super_admin');
}

async function ensureFamily(client, familyId, familyRecord) {
    const existing = await client.query(
        `SELECT id FROM ${TABLES.families} WHERE family_id = $1`,
        [familyId]
    );
    if (existing.rows.length > 0) {
        console.log(`  ♻️  Family ${familyId} already exists, syncing child-scoped data...`);
        return existing.rows[0].id;
    }

    const email = familyRecord.email;
    const adminPassword = familyRecord.admin_password || familyRecord.adminPassword;
    if (!email || !adminPassword) {
        throw new Error(`Family ${familyId} is missing email or admin_password in families.json`);
    }

    const result = await client.query(
        `INSERT INTO ${TABLES.families}
            (family_id, email, admin_password, is_blocked, is_verified, verification_token, created_at, last_activity)
         VALUES ($1, $2, $3, $4, $5, $6, COALESCE($7, NOW()), COALESCE($8, NOW()))
         RETURNING id`,
        [
            familyId,
            email,
            adminPassword,
            familyRecord.isBlocked === true || familyRecord.is_blocked === true,
            familyRecord.isVerified !== false && familyRecord.is_verified !== false,
            familyRecord.verification_token || familyRecord.verificationToken || null,
            familyRecord.created_at || null,
            familyRecord.last_activity || null
        ]
    );

    console.log(`  ✅ Migrated family: ${familyId} (${email})`);
    return result.rows[0].id;
}

async function syncChildren(client, familyDbId, snapshot) {
    const existing = await client.query(
        `SELECT id, name, token
         FROM ${TABLES.children}
         WHERE family_id = $1
         ORDER BY id ASC`,
        [familyDbId]
    );
    const unmatchedExisting = [...existing.rows];
    const childIdByKey = new Map();

    for (let index = 0; index < snapshot.children.length; index += 1) {
        const child = snapshot.children[index];
        const remainingSourceChildren = snapshot.children.length - index;
        let matched = null;

        if (child.token) {
            matched = takeExistingChild(unmatchedExisting, (row) => row.token === child.token);
        }
        if (!matched) {
            matched = takeExistingChild(unmatchedExisting, (row) => row.name.toLowerCase() === child.name.toLowerCase());
        }
        if (!matched && snapshot.children.length === 1 && existing.rows.length === 1) {
            matched = takeExistingChild(unmatchedExisting, () => true);
        }
        if (!matched && unmatchedExisting.length === remainingSourceChildren) {
            matched = unmatchedExisting.shift() || null;
        }

        if (matched) {
            await client.query(
                `UPDATE ${TABLES.children}
                 SET name = $2,
                     token = COALESCE($3, token),
                     balance = $4,
                     monthly_limit = $5,
                     daily_coin_limit = $6,
                     theme = $7
                 WHERE id = $1`,
                [
                    matched.id,
                    child.name,
                    child.token,
                    child.balance,
                    child.monthlyLimit,
                    child.dailyCoinLimit,
                    child.theme
                ]
            );
            childIdByKey.set(child.legacyKey, matched.id);
            continue;
        }

        const inserted = await client.query(
            `INSERT INTO ${TABLES.children}
                (family_id, name, token, balance, monthly_limit, daily_coin_limit, theme, created_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7, COALESCE($8, NOW()))
             RETURNING id`,
            [
                familyDbId,
                child.name,
                child.token,
                child.balance,
                child.monthlyLimit,
                child.dailyCoinLimit,
                child.theme,
                child.createdAt
            ]
        );
        childIdByKey.set(child.legacyKey, inserted.rows[0].id);
    }

    return childIdByKey;
}

function takeExistingChild(unmatchedExisting, predicate) {
    const index = unmatchedExisting.findIndex(predicate);
    if (index === -1) {
        return null;
    }
    return unmatchedExisting.splice(index, 1)[0];
}

async function syncLastSelectedChild(client, familyDbId, childId) {
    await client.query(
        `UPDATE ${TABLES.families}
         SET last_selected_child_id = $2
         WHERE id = $1`,
        [familyDbId, childId]
    );
}

async function replaceTasks(client, familyDbId, snapshot, childIdByKey) {
    await client.query(`DELETE FROM ${TABLES.tasks} WHERE family_id = $1`, [familyDbId]);

    for (const task of snapshot.tasks) {
        const childId = childIdByKey.get(task.childKey);
        if (!childId) {
            continue;
        }
        await client.query(
            `INSERT INTO ${TABLES.tasks}
                (family_id, child_id, task_id, name, coins, group_name, frequency, comment, money_limit, is_deleted)
             VALUES ($1, $2, $3, $4, $5, $6, CAST($7 AS JSONB), $8, $9, $10)`,
            [
                familyDbId,
                childId,
                task.taskId,
                task.name,
                task.coins,
                task.groupName,
                task.frequency,
                task.comment,
                task.moneyLimit,
                task.deleted
            ]
        );
    }

    console.log(`    📝 Synced ${snapshot.tasks.length} tasks`);
}

async function replaceShopItems(client, familyDbId, snapshot, childIdByKey) {
    await client.query(`DELETE FROM ${TABLES.shopItems} WHERE family_id = $1`, [familyDbId]);

    for (const item of snapshot.shopItems) {
        const childId = childIdByKey.get(item.childKey);
        if (!childId) {
            continue;
        }
        await client.query(
            `INSERT INTO ${TABLES.shopItems}
                (family_id, child_id, item_id, name, price, group_name, frequency, money_limit, is_deleted)
             VALUES ($1, $2, $3, $4, $5, $6, CAST($7 AS JSONB), $8, $9)`,
            [
                familyDbId,
                childId,
                item.itemId,
                item.name,
                item.price,
                item.groupName,
                item.frequency,
                item.moneyLimit,
                item.deleted
            ]
        );
    }

    console.log(`    🛍️  Synced ${snapshot.shopItems.length} shop items`);
}

async function replaceHistory(client, familyDbId, snapshot, childIdByKey) {
    await client.query(`DELETE FROM ${TABLES.history} WHERE family_id = $1`, [familyDbId]);

    for (const entry of snapshot.historyEntries) {
        const childId = childIdByKey.get(entry.childKey);
        if (!childId) {
            continue;
        }
        await client.query(
            `INSERT INTO ${TABLES.history}
                (family_id, child_id, external_id, type, amount, description, money_amount, related_id, group_name, comment, created_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, COALESCE($11, NOW()))`,
            [
                familyDbId,
                childId,
                entry.externalId,
                entry.type,
                entry.amount,
                entry.description,
                entry.moneyAmount,
                entry.relatedId,
                entry.groupName,
                entry.comment,
                entry.createdAt
            ]
        );
    }

    console.log(`    📜 Synced ${snapshot.historyEntries.length} history entries`);
}

async function replaceRequests(client, familyDbId, snapshot, childIdByKey) {
    await client.query(`DELETE FROM ${TABLES.requests} WHERE family_id = $1`, [familyDbId]);

    for (const request of snapshot.requests) {
        const childId = childIdByKey.get(request.childKey);
        if (!childId) {
            continue;
        }
        await client.query(
            `INSERT INTO ${TABLES.requests}
                (family_id, child_id, external_id, task_id, task_name, item_id, coins, status, request_type, money_amount, created_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, COALESCE($11, NOW()))`,
            [
                familyDbId,
                childId,
                request.externalId,
                request.taskId,
                request.taskName,
                request.itemId,
                request.coins,
                request.status,
                request.requestType,
                request.moneyAmount,
                request.createdAt
            ]
        );
    }

    console.log(`    📨 Synced ${snapshot.requests.length} requests`);
}

async function syncFamilyData(client, familyDbId, familyId, familyRecord) {
    const familyPayload = loadFamilyPayload(familyId);
    const snapshot = buildLegacyFamilySnapshot(familyId, familyRecord, familyPayload.data);
    const childIdByKey = await syncChildren(client, familyDbId, snapshot);
    const childIds = Array.from(new Set(childIdByKey.values()));
    const preferredChildId = childIdByKey.get(snapshot.preferredChildKey) || childIds[0] || null;
    await syncLastSelectedChild(client, familyDbId, preferredChildId);

    if (snapshot.hasScopedData || familyPayload.exists) {
        await replaceTasks(client, familyDbId, snapshot, childIdByKey);
        await replaceShopItems(client, familyDbId, snapshot, childIdByKey);
        await replaceHistory(client, familyDbId, snapshot, childIdByKey);
        await replaceRequests(client, familyDbId, snapshot, childIdByKey);
    } else {
        console.log(`    ⏭️  No scoped JSON data for ${familyId}`);
    }

    return {
        dbId: familyDbId,
        childIds,
        primaryChildId: preferredChildId,
        friendFamilyIds: snapshot.friendFamilyIds
    };
}

function loadFamilyPayload(familyId) {
    const familyFile = path.join(FAMILIES_DATA_DIR, `${familyId}.json`);
    if (!fs.existsSync(familyFile)) {
        return { exists: false, data: {} };
    }

    return {
        exists: true,
        data: JSON.parse(fs.readFileSync(familyFile, 'utf8'))
    };
}

async function rebuildFriends(client, familyIdMap) {
    console.log('\n🔗 Rebuilding friend relationships...');

    const migratedChildIds = Array.from(new Set(
        Object.values(familyIdMap).flatMap((info) => info.childIds)
    ));

    if (migratedChildIds.length > 0) {
        await client.query(
            `DELETE FROM ${TABLES.friends}
             WHERE child_id = ANY($1) OR friend_child_id = ANY($1)`,
            [migratedChildIds]
        );
    }

    const insertedPairs = new Set();
    for (const [familyId, sourceInfo] of Object.entries(familyIdMap)) {
        for (const friendFamilyId of sourceInfo.friendFamilyIds) {
            const targetInfo = familyIdMap[String(friendFamilyId)];
            if (!targetInfo) {
                console.log(`  ⚠️  Friend family ${friendFamilyId} not found for ${familyId}`);
                continue;
            }

            await insertFriendPair(client, insertedPairs, sourceInfo.primaryChildId, targetInfo.primaryChildId);
            await insertFriendPair(client, insertedPairs, targetInfo.primaryChildId, sourceInfo.primaryChildId);
        }
    }

    console.log('  ✅ Friend relationships rebuilt');
}

async function insertFriendPair(client, insertedPairs, childId, friendChildId) {
    if (!childId || !friendChildId) {
        return;
    }
    const pairKey = `${childId}:${friendChildId}`;
    if (insertedPairs.has(pairKey)) {
        return;
    }

    await client.query(
        `INSERT INTO ${TABLES.friends} (child_id, friend_child_id)
         VALUES ($1, $2)
         ON CONFLICT (child_id, friend_child_id) DO NOTHING`,
        [childId, friendChildId]
    );
    insertedPairs.add(pairKey);
}

async function shouldSkipMigration() {
    process.stdout.write('📊 Checking if data migration from JSON is needed...');
    if (!fs.existsSync(FAMILIES_FILE)) {
        console.log(' ✅ (no JSON data to migrate)');
        return true;
    }
    console.log(' ✅');
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
        const familyRecord = families[familyId] || {};
        const familyDbId = await ensureFamily(client, familyId, familyRecord);
        familyIdMap[familyId] = await syncFamilyData(client, familyDbId, familyId, familyRecord);
    }
    return familyIdMap;
}

async function runDataMigration() {
    if (await shouldSkipMigration()) {
        return;
    }

    console.log('\n🚀 Starting child-aware data migration from JSON to PostgreSQL...');
    const client = await getClient();
    try {
        await client.query('BEGIN');
        const { superAdmin, families, familyIds } = await loadMigrationData();
        console.log(`📋 Found ${familyIds.length} families to sync`);

        await migrateSuperAdmin(client, superAdmin);
        const familyIdMap = await migrateAllFamilies(client, familyIds, families);
        await rebuildFriends(client, familyIdMap);

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
        .catch((err) => {
            console.error(err);
            process.exit(1);
        });
}

module.exports = { runDataMigration };
