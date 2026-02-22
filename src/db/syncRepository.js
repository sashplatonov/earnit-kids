const { query } = require('./connection');

/**
 * Handle balance synchronization for children
 */
async function syncBalances(client, data, actingChildId) {
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
async function syncTasks(client, syncContext) {
    const { dbId, data, actingChildId, defaultChildId, deleteWhere, deleteParams } = syncContext;
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
async function syncShop(client, syncContext) {
    const { dbId, data, actingChildId, defaultChildId, deleteWhere, deleteParams } = syncContext;
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

module.exports = {
    syncBalances,
    syncTasks,
    syncShop
};
