const { query } = require('./connection');

function val(v, def) { return (v === undefined || v === null) ? def : v; }

/**
 * Handle balance synchronization for children
 */
async function syncBalances({ client, data, actingChildId }) {
    if (data.balance !== undefined && actingChildId) {
        await client.query('UPDATE children SET balance = $1 WHERE id = $2', [data.balance, actingChildId]);
    }

    if (!Array.isArray(data.children) || actingChildId) return;

    for (const child of data.children) {
        if (!child.id || child.balance === undefined) continue;
        await client.query('UPDATE children SET balance = $1 WHERE id = $2', [child.balance, child.id]);
    }
}

/**
 * Sync tasks for a family/child
 */
async function syncTasks({ client, dbId, data, actingChildId, defaultChildId, deleteWhere, deleteParams }) {
    if (data.tasks === undefined) return;

    await client.query(`DELETE FROM tasks ${deleteWhere}`, deleteParams);
    for (const task of data.tasks) {
        const targetChildId = actingChildId || task.childId || defaultChildId;
        await client.query(
            `INSERT INTO tasks (family_id, child_id, task_id, name, coins, group_name, frequency, comment, money_limit)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)`,
            [
                dbId, targetChildId, task.id, task.name, task.coins || 0,
                task.group || null, task.frequency ? JSON.stringify(task.frequency) : null,
                task.comment || null, task.money_limit || null
            ]
        );
    }
}

/**
 * Sync shop items for a family/child
 */
async function syncShop({ client, dbId, data, actingChildId, defaultChildId, deleteWhere, deleteParams }) {
    if (data.shop === undefined) return;

    await client.query(`DELETE FROM shop_items ${deleteWhere}`, deleteParams);
    for (const item of data.shop) {
        const targetChildId = actingChildId || item.childId || defaultChildId;
        await client.query(
            `INSERT INTO shop_items (family_id, child_id, item_id, name, price, group_name, frequency, money_limit)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
            [
                dbId, targetChildId, item.id, item.name, item.price || 0,
                item.group || null, item.frequency ? JSON.stringify(item.frequency) : null,
                item.money_limit || null
            ]
        );
    }
}

async function deleteHistory({ client, dbId, data, actingChildId, defaultChildId }) {
    if (actingChildId) {
        return await client.query('DELETE FROM history WHERE child_id = $1', [actingChildId]);
    }
    const historyIds = data.history.map((entry) => entry.id).filter((id) => id);
    if (historyIds.length > 0) {
        const targetChildIds = [...new Set(data.history.map((entry) => entry.childId || defaultChildId))];
        return await client.query('DELETE FROM history WHERE family_id = $1 AND child_id = ANY($2)', [dbId, targetChildIds]);
    }
}

function getRelatedId(e) {
    if (e.itemId) return e.itemId;
    if (e.taskId) return e.taskId;
    return val(e.relatedId, null);
}

function getHistoryParams(dbId, targetId, e) {
    const relId = getRelatedId(e);
    const date = val(e.date, val(e.timestamp, new Date()));
    return [dbId, targetId, val(e.id, null), val(e.type, 'unknown'), val(e.amount, 0), val(e.description, ''), val(e.moneyAmount, 0), relId, date];
}

/**
 * Sync history for a family/child
 */
async function syncHistory(common) {
    const { client, dbId, data, actingChildId, defaultChildId } = common;
    if (!Array.isArray(data.history)) return;

    await deleteHistory(common);
    for (const e of data.history) {
        const targetId = actingChildId || e.childId || defaultChildId;
        const p = getHistoryParams(dbId, targetId, e);
        await client.query(`INSERT INTO history (family_id, child_id, external_id, type, amount, description, money_amount, related_id, created_at) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)`, p);
    }
}

function getReqParams(dbId, targetId, r) {
    const date = val(r.date, val(r.created_at, new Date()));
    return [
        dbId, targetId, val(r.id, null), val(r.requestType, 'earn'),
        val(r.taskId, null), val(r.itemId, null), val(r.taskName, ''),
        val(r.coins, 0), val(r.moneyAmount, 0), val(r.status, 'pending'), date
    ];
}

/**
 * Sync requests for a family/child
 */
async function syncRequests({ client, dbId, data, actingChildId, defaultChildId, deleteWhere, deleteParams }) {
    if (!Array.isArray(data.requests)) return;

    await client.query(`DELETE FROM requests ${deleteWhere}`, deleteParams);
    for (const r of data.requests) {
        const targetId = actingChildId || r.childId || defaultChildId;
        const p = getReqParams(dbId, targetId, r);
        await client.query(`INSERT INTO requests (family_id, child_id, external_id, request_type, task_id, item_id, task_name, coins, money_amount, status, created_at) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)`, p);
    }
}

/**
 * Sync friends for a child
 */
async function syncFriends({ client, data, actingChildId }) {
    if (!Array.isArray(data.friends) || !actingChildId) return;

    await client.query('DELETE FROM friends WHERE child_id = $1', [actingChildId]);
    for (const friendItem of data.friends) {
        const friendChildId = typeof friendItem === 'object' ? friendItem.friendChildId : null;
        if (!friendChildId) continue;
        await client.query(
            'INSERT INTO friends (child_id, friend_child_id) VALUES ($1, $2) ON CONFLICT DO NOTHING',
            [actingChildId, friendChildId]
        );
    }
}

module.exports = {
    syncBalances,
    syncTasks,
    syncShop,
    syncHistory,
    syncRequests,
    syncFriends
};
