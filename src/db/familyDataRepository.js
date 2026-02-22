/**
 * Family Data Repository - Database access layer for family data
 */

const { query, getClient } = require('./connection');
const familyRepository = require('./familyRepository');
const syncRepository = require('./syncRepository');
const { resolveDefaultChildId, buildDeleteScope } = require('./syncUtils');

const DEFAULT_FAMILY_DATA = {
    balance: 0,
    tasks: [],
    shop: [],
    history: [],
    requests: [],
    friends: []
};

function val(v, d) { return (v === undefined || v === null) ? d : v; }

function mapTask(row) {
    return {
        id: row.task_id,
        childId: row.child_id,
        name: row.name,
        coins: row.coins,
        group: row.group_name,
        frequency: row.frequency,
        comment: row.comment,
        money_limit: row.money_limit,
        isDeleted: !!row.is_deleted
    };
}

function mapShopItem(row) {
    return {
        id: row.item_id,
        childId: row.child_id,
        name: row.name,
        price: row.price,
        group: row.group_name,
        frequency: row.frequency,
        money_limit: row.money_limit,
        isDeleted: !!row.is_deleted
    };
}

function mapHistoryEntry(row) {
    const entry = {
        id: row.external_id ? parseInt(row.external_id) : row.id,
        childId: row.child_id,
        type: row.type,
        amount: row.amount,
        description: row.description,
        group: row.group_name,
        comment: row.comment,
        date: row.created_at,
        moneyAmount: row.money_amount
    };

    if (row.type === 'spend' && row.related_id) entry.itemId = parseInt(row.related_id);
    else if (row.type === 'earn' && row.related_id) entry.taskId = parseInt(row.related_id);

    if (row.related_id) entry.relatedId = parseInt(row.related_id);

    return entry;
}

async function fetchFriendsByChild(childId) {
    const sql = `
        SELECT fr.friend_child_id, c_friend.name as friend_name, f_friend.id as friend_family_id
        FROM friends fr
        JOIN children c_friend ON fr.friend_child_id = c_friend.id
        JOIN families f_friend ON c_friend.family_id = f_friend.id
        WHERE fr.child_id = $1
    `;
    const res = await query(sql, [childId]);
    return res.rows.map(row => ({ friendChildId: row.friend_child_id, name: row.friend_name, familyId: row.friend_family_id }));
}

async function fetchFriendsByFamily(dbId) {
    const sql = `
        SELECT fr.friend_child_id, c_friend.name as friend_name, f_friend.id as friend_family_id, fr.child_id as owner_child_id
        FROM friends fr
        JOIN children c_owner ON fr.child_id = c_owner.id
        JOIN children c_friend ON fr.friend_child_id = c_friend.id
        JOIN families f_friend ON c_friend.family_id = f_friend.id
        WHERE c_owner.family_id = $1
    `;
    const res = await query(sql, [dbId]);
    return res.rows.map(row => ({ friendChildId: row.friend_child_id, name: row.friend_name, familyId: row.friend_family_id, ownerChildId: row.owner_child_id }));
}

async function fetchFriends(dbId, childId) {
    try {
        if (childId) return await fetchFriendsByChild(childId);
        return await fetchFriendsByFamily(dbId);
    } catch (e) {
        console.warn("Friends query failed", e.message);
        return [];
    }
}

async function getFamilyData(familyId, childId = null) {
    const dbId = await familyRepository.getDbId(familyId);
    if (!dbId) return { ...DEFAULT_FAMILY_DATA };

    const p = childId ? [dbId, childId] : [dbId];
    const w = childId ? 'WHERE family_id = $1 AND child_id = $2' : 'WHERE family_id = $1';

    const [tRes, sRes, hRes, qRes, bRes] = await Promise.all([
        query(`SELECT * FROM tasks ${w} ORDER BY created_at`, p),
        query(`SELECT * FROM shop_items ${w} ORDER BY created_at`, p),
        query(`SELECT * FROM history ${w} ORDER BY created_at DESC LIMIT 200`, p),
        query(`
            SELECT r.*, t.group_name as task_group, t.comment as task_comment, s.group_name as item_group
            FROM requests r
            LEFT JOIN tasks t ON r.task_id = t.task_id AND r.family_id = t.family_id
            LEFT JOIN shop_items s ON r.item_id = s.item_id AND r.family_id = s.family_id
            ${childId ? 'WHERE r.family_id = $1 AND r.child_id = $2' : 'WHERE r.family_id = $1'}
            ORDER BY r.created_at DESC`, p),
        childId ? query('SELECT balance FROM children WHERE id = $1', [childId]) : { rows: [] }
    ]);

    return {
        balance: bRes.rows[0]?.balance || 0,
        tasks: tRes.rows.map(mapTask),
        shop: sRes.rows.map(mapShopItem),
        history: hRes.rows.map(mapHistoryEntry),
        requests: qRes.rows.map(row => ({
            id: row.external_id ? parseInt(row.external_id) : row.id,
            childId: row.child_id,
            requestType: val(row.request_type, 'earn'),
            taskId: row.task_id ? parseInt(row.task_id) : null,
            itemId: row.item_id ? parseInt(row.item_id) : null,
            taskName: row.task_name,
            coins: row.coins,
            moneyAmount: val(row.money_amount, 0),
            status: row.status,
            date: row.created_at,
            group: row.task_group || row.item_group,
            comment: row.task_comment
        })),
        friends: await fetchFriends(dbId, childId)
    };
}

async function saveFamilyData(familyId, data, actingChildId = null) {
    const dbId = await familyRepository.getDbId(familyId);
    const defaultChildId = await resolveDefaultChildId(familyId, actingChildId);
    if (!dbId || !defaultChildId) return false;

    const { deleteWhere, deleteParams } = buildDeleteScope(dbId, actingChildId);
    const client = await getClient();
    try {
        await client.query('BEGIN');
        const c = { client, dbId, data, actingChildId, defaultChildId, deleteWhere, deleteParams };
        await syncRepository.syncBalances(c);
        await syncRepository.syncTasks(c);
        await syncRepository.syncShop(c);
        await syncRepository.syncHistory(c);
        await syncRepository.syncRequests(c);
        await syncRepository.syncFriends(c);
        await client.query('COMMIT');
        return true;
    } catch (err) {
        await client.query('ROLLBACK');
        console.error('Error saving family data:', err.message);
        return false;
    } finally { client.release(); }
}

function getInterval(tf) {
    if (tf === 'week') return '7 days';
    if (tf === 'year') return '1 year';
    return '1 month';
}

async function getAnalyticsData(familyId, childId, timeframe = 'month') {
    const dbId = await familyRepository.getDbId(familyId);
    if (!dbId) return { summary: {}, topTasks: [], topItems: [] };
    const int = getInterval(timeframe);
    const p = childId ? [dbId, int, childId] : [dbId, int];
    const f = childId ? ' AND h.child_id = $3' : '';

    const qs = `SELECT SUM(CASE WHEN type='earn' THEN amount ELSE 0 END) as e, SUM(CASE WHEN type='spend' THEN amount ELSE 0 END) as s FROM history h WHERE h.family_id=$1 AND h.created_at >= NOW() - $2::interval${f}`;
    const qt = `
        SELECT COALESCE(t.name, h.description, 'Задание') as n, SUM(h.amount) as c, COUNT(*) as ct 
        FROM history h 
        LEFT JOIN tasks t ON h.related_id = t.task_id AND h.family_id = t.family_id
        WHERE h.family_id=$1 AND h.type='earn' AND h.created_at >= NOW() - $2::interval${f} 
        GROUP BY n ORDER BY c DESC`;
    const qi = `
        SELECT COALESCE(i.name, h.description, 'Товар') as n, SUM(h.amount) as c, COUNT(*) as ct 
        FROM history h 
        LEFT JOIN shop_items i ON h.related_id = i.item_id AND h.family_id = i.family_id
        WHERE h.family_id=$1 AND h.type='spend' AND h.created_at >= NOW() - $2::interval${f} 
        GROUP BY n ORDER BY c DESC
    `;

    const [sr, tr, ir] = await Promise.all([query(qs, p), query(qt, p), query(qi, p)]);
    const s = sr.rows[0];
    const mapR = r => ({ name: r.n, coins: parseInt(r.c), count: parseInt(r.ct) });
    return {
        summary: { totalEarned: parseInt(s.e || 0), totalSpent: parseInt(s.s || 0), netChange: parseInt((s.e || 0) - (s.s || 0)) },
        topTasks: tr.rows.map(mapR),
        topItems: ir.rows.map(mapR)
    };
}

function getHistoryParams(dbId, e) {
    const relId = e.itemId || e.taskId || e.relatedId || null;
    const d = val(e.date, val(e.timestamp, new Date()));
    return [dbId, e.childId, val(e.id, null), val(e.type, 'unknown'), val(e.amount, 0), val(e.description, ''), val(e.moneyAmount, 0), relId, d];
}

async function addHistoryEntry(familyId, e) {
    const dbId = await familyRepository.getDbId(familyId);
    if (!dbId || !e.childId) return false;
    const p = getHistoryParams(dbId, e);
    const res = await query(`INSERT INTO history (family_id, child_id, external_id, type, amount, description, money_amount, related_id, created_at) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)`, p);
    return res.rowCount > 0;
}

async function addRequest(familyId, r) {
    const dbId = await familyRepository.getDbId(familyId);
    if (!dbId || !r.childId) return null;
    const d = val(r.date, val(r.created_at, new Date()));
    const p = [dbId, r.childId, val(r.id, null), val(r.requestType, 'earn'), val(r.taskId, null), val(r.itemId, null), val(r.taskName, ''), val(r.coins, 0), val(r.moneyAmount, 0), val(r.status, 'pending'), d];
    const res = await query(`INSERT INTO requests (family_id, child_id, external_id, request_type, task_id, item_id, task_name, coins, money_amount, status, created_at) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11) RETURNING id`, p);
    return res.rows[0]?.id || null;
}

module.exports = {
    DEFAULT_FAMILY_DATA, getFamilyData, saveFamilyData, getAnalyticsData, addHistoryEntry, addRequest,
    updateBalance: (fid, b, cid) => query('UPDATE children SET balance=$1 WHERE id=$2', [b, cid]).then(r => r.rowCount > 0),
    updateRequestStatus: (id, s) => query('UPDATE requests SET status=$1, updated_at=NOW() WHERE id=$2', [s, id]).then(r => r.rowCount > 0),
    addFriend: (cid, fcid) => query('INSERT INTO friends (child_id, friend_child_id) VALUES ($1, $2)', [cid, fcid]).then(() => true).catch(err => err.code === '23505' ? false : Promise.reject(err)),
    getFriendsData: (fid, cid) => query('SELECT fr.friend_child_id, c.name, c.balance FROM friends fr JOIN children c ON fr.friend_child_id = c.id WHERE fr.child_id = $1', [cid]).then(r => r.rows.map(row => ({ id: row.friend_child_id, nickname: row.name, balance: row.balance })))
};
