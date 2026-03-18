/** @file Family Data Repository PostgreSQL data access */
/**
 * Family Data Repository - Database access layer for family data
 */

const { query, getClient } = require('./connection');
const familyRepository = require('./familyRepository');
const syncRepository = require('./syncRepository');
const { resolveDefaultChildId, buildDeleteScope } = require('./syncUtils');
const cache = require('../utils/Cache');
const { val, mapTask, mapShopItem, mapHistoryEntry, mapRequest, getHistoryParams } = require('./familyDataMappers');
const { getAnalyticsData } = require('./analyticsRepository');

const DEFAULT_FAMILY_DATA = {
    balance: 0,
    tasks: [],
    shop: [],
    history: [],
    requests: [],
    friends: []
};

async function fetchFriends(dbId, childId) {
    if (!childId) return [];
    const sql = `SELECT fr.friend_child_id, c.name, c.balance FROM friends fr JOIN children c ON fr.friend_child_id = c.id WHERE fr.child_id = $1`;
    const res = await query(sql, [childId]);
    return res.rows.map(row => ({ id: row.friend_child_id, nickname: row.name, balance: row.balance }));
}

async function fetchFamilyDataRaw({ dbId, childId, p, hw, rw }) {
    const client = await getClient();
    try {
        const tasksFilter = childId ? ' AND t.child_id = $2' : '';
        const shopFilter = childId ? ' AND s.child_id = $2' : '';
        const taskParams = childId ? [dbId, childId] : [dbId];
        const shopParams = childId ? [dbId, childId] : [dbId];

        return await Promise.all([
            client.query(`SELECT t.*, t.group_name FROM tasks t WHERE t.family_id = $1 AND t.is_deleted = false${tasksFilter}`, taskParams),
            client.query(`SELECT s.*, s.group_name FROM shop_items s WHERE s.family_id = $1 AND s.is_deleted = false${shopFilter}`, shopParams),
            client.query(`SELECT h.* FROM history h ${hw} ORDER BY h.created_at DESC LIMIT 50`, p),
            client.query(`SELECT r.*, t.name as task_name, i.name as item_name, t.group_name as task_group, i.group_name as item_group, t.comment as task_comment 
                FROM requests r 
                LEFT JOIN tasks t ON r.task_id = t.task_id 
                LEFT JOIN shop_items i ON r.item_id = i.item_id 
                ${rw} 
                ORDER BY r.created_at DESC`, p),
            childId ? client.query('SELECT balance FROM children WHERE id = $1', [childId]) : { rows: [] }
        ]);
    } finally {
        client.release();
    }
}

async function getFamilyData(familyId, childId = null) {
    const cacheKey = `familyData:${familyId}:${childId || 'all'}`;
    const cached = cache.get(cacheKey);
    if (cached) return cached;

    const dbId = await familyRepository.getDbId(familyId);
    if (!dbId) return { ...DEFAULT_FAMILY_DATA };

    const p = childId ? [dbId, childId] : [dbId];
    const hw = childId ? 'WHERE h.family_id = $1 AND h.child_id = $2' : 'WHERE h.family_id = $1';
    const rw = childId ? 'WHERE r.family_id = $1 AND r.child_id = $2' : 'WHERE r.family_id = $1';

    const [tRes, sRes, hRes, qRes, bRes] = await fetchFamilyDataRaw({ dbId, childId, p, hw, rw });

    const result = {
        balance: bRes.rows[0]?.balance || 0,
        tasks: tRes.rows.map(mapTask),
        shop: sRes.rows.map(mapShopItem),
        history: hRes.rows.map(mapHistoryEntry),
        requests: qRes.rows.map(mapRequest),
        friends: await fetchFriends(dbId, childId)
    };

    cache.set(cacheKey, result);
    return result;
}

async function execSync(c) {
    return await Promise.all([
        syncRepository.syncBalances(c),
        syncRepository.syncTasks(c),
        syncRepository.syncShop(c),
        syncRepository.syncHistory(c),
        syncRepository.syncRequests(c),
        syncRepository.syncFriends(c)
    ]);
}

async function saveFamilyData(familyId, data, actingChildId = null) {
    const dbId = await familyRepository.getDbId(familyId);
    const defaultChildId = await resolveDefaultChildId(familyId, actingChildId);
    if (!dbId || !defaultChildId) return false;

    const { deleteWhere, deleteParams } = buildDeleteScope(dbId, actingChildId);
    const client = await getClient();
    try {
        await client.query('BEGIN');
        await execSync({ client, dbId, data, actingChildId, defaultChildId, deleteWhere, deleteParams });
        await client.query('COMMIT');

        cache.invalidatePrefix(`familyData:${familyId}`);
        cache.invalidatePrefix(`analytics:${familyId}`);
        return true;
    } catch (err) {
        await client.query('ROLLBACK');
        return false;
    } finally { client.release(); }
}

async function addHistoryEntry(familyId, e) {
    const dbId = await familyRepository.getDbId(familyId);
    if (!dbId || !e.childId) return false;
    const p = getHistoryParams(dbId, e);
    const res = await query(`INSERT INTO history (family_id, child_id, external_id, type, amount, description, money_amount, related_id, created_at) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)`, p);
    if (res.rowCount > 0) {
        cache.invalidatePrefix(`familyData:${familyId}`);
        cache.invalidatePrefix(`analytics:${familyId}`);
        return true;
    }
    return false;
}

async function addRequest(familyId, r) {
    const dbId = await familyRepository.getDbId(familyId);
    if (!dbId || !r.childId) return null;
    const d = val(r.date, val(r.created_at, new Date()));
    const p = [dbId, r.childId, val(r.id, null), val(r.requestType, 'earn'), val(r.taskId, null), val(r.itemId, null), val(r.taskName, ''), val(r.coins, 0), val(r.moneyAmount, 0), val(r.status, 'pending'), d];
    const res = await query(`INSERT INTO requests (family_id, child_id, external_id, request_type, task_id, item_id, task_name, coins, money_amount, status, created_at) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11) RETURNING id`, p);

    const id = res.rows[0]?.id || null;
    if (id) {
        cache.invalidatePrefix(`familyData:${familyId}`);
    }
    return id;
}

async function getPaginatedHistory(familyId, childId = null, { page = 1, limit = 50 } = {}) {
    const dbId = await familyRepository.getDbId(familyId);
    if (!dbId) return { data: [], total: 0 };

    const offset = (page - 1) * limit;
    const f = childId ? ' AND h.child_id = $2' : '';
    const p = childId ? [dbId, childId] : [dbId];

    const [countRes, dataRes] = await Promise.all([
        query(`SELECT COUNT(*) FROM history h WHERE h.family_id = $1${f}`, p),
        query(`SELECT h.* FROM history h WHERE h.family_id = $1${f} ORDER BY h.created_at DESC LIMIT ${parseInt(limit)} OFFSET ${parseInt(offset)}`, p)
    ]);

    return {
        data: dataRes.rows.map(mapHistoryEntry),
        total: parseInt(countRes.rows[0].count)
    };
}

async function getPaginatedRequests(familyId, childId = null, { page = 1, limit = 50 } = {}) {
    const dbId = await familyRepository.getDbId(familyId);
    if (!dbId) return { data: [], total: 0 };

    const offset = (page - 1) * limit;
    const f = childId ? ' AND r.child_id = $2' : '';
    const p = childId ? [dbId, childId] : [dbId];

    const [countRes, dataRes] = await Promise.all([
        query(`SELECT COUNT(*) FROM requests r WHERE r.family_id = $1${f}`, p),
        query(`SELECT r.*, t.name as task_name, i.name as item_name, t.group_name as task_group, i.group_name as item_group, t.comment as task_comment 
            FROM requests r 
            LEFT JOIN tasks t ON r.task_id = t.task_id 
            LEFT JOIN shop_items i ON r.item_id = i.item_id 
            WHERE r.family_id = $1${f} 
            ORDER BY r.created_at DESC LIMIT ${parseInt(limit)} OFFSET ${parseInt(offset)}`, p)
    ]);

    return {
        data: dataRes.rows.map(mapRequest),
        total: parseInt(countRes.rows[0].count)
    };
}

module.exports = {
    DEFAULT_FAMILY_DATA, getFamilyData, saveFamilyData, getAnalyticsData, addHistoryEntry, addRequest,
    getPaginatedHistory, getPaginatedRequests,
    updateBalance: (fid, b, cid) => {
        cache.invalidatePrefix(`familyData:${fid}`);
        return query('UPDATE children SET balance=$1 WHERE id=$2 AND family_id=(SELECT id FROM families WHERE family_id=$3)', [b, cid, fid]).then(r => r.rowCount > 0);
    },
    updateRequestStatus: (fid, id, s) => {
        cache.invalidatePrefix(`familyData:${fid}`);
        return query('UPDATE requests SET status=$1, updated_at=NOW() WHERE id=$2 AND family_id=(SELECT id FROM families WHERE family_id=$3)', [s, id, fid]).then(r => r.rowCount > 0);
    },
    addFriend: (fid, cid, fcid) => {
        cache.invalidatePrefix(`familyData:${fid}`);
        return query('INSERT INTO friends (child_id, friend_child_id) VALUES ($1, $2)', [cid, fcid]).then(() => true).catch(err => err.code === '23505' ? false : Promise.reject(err));
    },
    getFriendsData: (fid, cid) => query('SELECT fr.friend_child_id, c.name, c.balance FROM friends fr JOIN children c ON fr.friend_child_id = c.id WHERE fr.child_id = $1', [cid]).then(r => r.rows.map(row => ({ id: row.friend_child_id, nickname: row.name, balance: row.balance })))
};
