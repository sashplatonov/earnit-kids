/**
 * Family Data Repository - Database access layer for family data
 * Updated for multi-child support
 */

const { query, getClient } = require('./connection');
const familyRepository = require('./familyRepository');

const DEFAULT_FAMILY_DATA = {
    balance: 0,
    tasks: [],
    shop: [],
    history: [],
    requests: [],
    friends: []
};

/**
 * Get family data (filtered by childId if provided)
 * @param {string} familyId 
 * @param {number|null} childId - If provided, return data only for this child
 * @returns {Promise<Object>}
 */
async function getFamilyData(familyId, childId = null) {
    const dbId = await familyRepository.getDbId(familyId);
    if (!dbId) return { ...DEFAULT_FAMILY_DATA };

    // Prepare filter
    const params = [dbId];
    let whereClause = 'WHERE family_id = $1';
    if (childId) {
        whereClause += ' AND child_id = $2';
        params.push(childId);
    }

    // Fetch Balance
    let balance = 0;
    if (childId) {
        const cRes = await query('SELECT balance FROM children WHERE id = $1', [childId]);
        balance = cRes.rows[0]?.balance || 0;
    } else {
        // For parent view, we might want sum? Or just 0 and let parent allow list breakdown
        // Let's return 0. The family object has children list with balances.
        balance = 0;
    }

    const [tasksResult, shopResult, historyResult, requestsResult] = await Promise.all([
        query(`SELECT * FROM tasks ${whereClause} ORDER BY created_at`, params),
        query(`SELECT * FROM shop_items ${whereClause} ORDER BY created_at`, params),
        query(`SELECT * FROM history ${whereClause} ORDER BY created_at DESC LIMIT 200`, params),
        query(`SELECT * FROM requests ${whereClause} ORDER BY created_at DESC`, params)
    ]);

    // Friends need special handling because of join
    let friendsSql = `
        SELECT f.family_id, f.child_nickname, c.name as friend_name, fr.friend_child_id
        FROM friends fr
        JOIN children c ON fr.friend_child_id = c.id
        JOIN families f ON c.family_id = f.id
        WHERE fr.family_id = $1
    `; // Note: family_id column on friends table is GONE.
    // We only have child_id and friend_child_id.

    // Correct Friends Logic:
    // We need to find friends where `child_id` belongs to our family (or is specific child).
    // Join children table to check family_id of the 'owner' child.

    let friendsParams = [];
    let friendsWhere = '';

    if (childId) {
        friendsSql = `
            SELECT fr.friend_child_id, c_friend.name as friend_name, f_friend.family_id as friend_family_id
            FROM friends fr
            JOIN children c_friend ON fr.friend_child_id = c_friend.id
            JOIN families f_friend ON c_friend.family_id = f_friend.id
            WHERE fr.child_id = $1
        `;
        friendsParams = [childId];
    } else {
        // All friends for all children in family
        friendsSql = `
            SELECT fr.friend_child_id, c_friend.name as friend_name, f_friend.family_id as friend_family_id, fr.child_id as owner_child_id
            FROM friends fr
            JOIN children c_owner ON fr.child_id = c_owner.id
            JOIN children c_friend ON fr.friend_child_id = c_friend.id
            JOIN families f_friend ON c_friend.family_id = f_friend.id
            WHERE c_owner.family_id = $1
        `;
        friendsParams = [dbId];
    }

    // Note: If previous migration failed regarding friends, this might fail.
    // But assuming migration succeeded.

    let friendsResult;
    try {
        friendsResult = await query(friendsSql, friendsParams);
    } catch (e) {
        console.warn("Friends query failed, maybe empty", e.message);
        friendsResult = { rows: [] };
    }

    return {
        balance,
        tasks: tasksResult.rows.map(row => ({
            id: row.task_id,
            childId: row.child_id,
            name: row.name,
            coins: row.coins,
            group: row.group_name,
            frequency: row.frequency,
            comment: row.comment,
            money_limit: row.money_limit
        })),
        shop: shopResult.rows.map(row => ({
            id: row.item_id,
            childId: row.child_id,
            name: row.name,
            price: row.price,
            group: row.group_name,
            frequency: row.frequency,
            money_limit: row.money_limit
        })),
        history: historyResult.rows.map(row => {
            const entry = {
                id: row.external_id ? parseInt(row.external_id) : row.id,
                childId: row.child_id,
                type: row.type,
                amount: row.amount,
                description: row.description,
                date: row.created_at,
                moneyAmount: row.money_amount
            };
            if (row.related_id) {
                if (row.type === 'spend') entry.itemId = parseInt(row.related_id);
                else if (row.type === 'earn') entry.taskId = parseInt(row.related_id);
                entry.relatedId = parseInt(row.related_id);
            }
            return entry;
        }),
        requests: requestsResult.rows.map(row => ({
            id: row.external_id ? parseInt(row.external_id) : row.id,
            childId: row.child_id,
            requestType: row.request_type || 'earn',
            taskId: row.task_id ? parseInt(row.task_id) : null,
            itemId: row.item_id ? parseInt(row.item_id) : null,
            taskName: row.task_name,
            coins: row.coins,
            moneyAmount: row.money_amount || 0,
            status: row.status,
            date: row.created_at
        })),
        friends: friendsResult.rows.map(row => ({
            friendChildId: row.friend_child_id,
            name: row.friend_name,
            familyId: row.friend_family_id,
            ownerChildId: row.owner_child_id // Only if parent view
        }))
    };
}

async function resolveDefaultChildId(familyId, actingChildId) {
    if (actingChildId) return actingChildId;

    const children = await familyRepository.getChildren(familyId);
    if (children.length > 0) return children[0].id;
    return null;
}

function buildDeleteScope(dbId, actingChildId) {
    if (!actingChildId) {
        return { deleteWhere: 'WHERE family_id = $1', deleteParams: [dbId] };
    }
    return {
        deleteWhere: 'WHERE family_id = $1 AND child_id = $2',
        deleteParams: [dbId, actingChildId]
    };
}

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
                dbId,
                targetChildId,
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
}

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
                dbId,
                targetChildId,
                item.id,
                item.name,
                item.price || 0,
                item.group || null,
                item.frequency ? JSON.stringify(item.frequency) : null,
                item.money_limit || null
            ]
        );
    }
}

async function deleteHistoryScope(client, dbId, data, actingChildId, defaultChildId) {
    if (actingChildId) {
        await client.query('DELETE FROM history WHERE child_id = $1', [actingChildId]);
        return;
    }

    const historyIds = data.history.map((entry) => entry.id).filter((id) => id);
    if (historyIds.length === 0) return;

    const targetChildIds = [...new Set(data.history.map((entry) => entry.childId || defaultChildId))];
    await client.query('DELETE FROM history WHERE family_id = $1 AND child_id = ANY($2)', [dbId, targetChildIds]);
}

async function syncHistory(client, dbId, data, actingChildId, defaultChildId) {
    if (!Array.isArray(data.history)) return;

    await deleteHistoryScope(client, dbId, data, actingChildId, defaultChildId);
    for (const entry of data.history) {
        const targetChildId = actingChildId || entry.childId || defaultChildId;
        const relatedId = entry.itemId || entry.taskId || entry.relatedId || null;
        await client.query(
            `INSERT INTO history (family_id, child_id, external_id, type, amount, description, money_amount, related_id, created_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)`,
            [
                dbId,
                targetChildId,
                entry.id || null,
                entry.type || 'unknown',
                entry.amount || 0,
                entry.description || '',
                entry.moneyAmount || 0,
                relatedId,
                entry.date || entry.timestamp || new Date()
            ]
        );
    }
}

async function syncRequests(client, syncContext) {
    const { dbId, data, actingChildId, defaultChildId, deleteWhere, deleteParams } = syncContext;
    if (!Array.isArray(data.requests)) return;

    await client.query(`DELETE FROM requests ${deleteWhere}`, deleteParams);
    for (const req of data.requests) {
        const targetChildId = actingChildId || req.childId || defaultChildId;
        await client.query(
            `INSERT INTO requests (family_id, child_id, external_id, request_type, task_id, item_id, task_name, coins, money_amount, status, created_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)`,
            [
                dbId,
                targetChildId,
                req.id || null,
                req.requestType || 'earn',
                req.taskId || null,
                req.itemId || null,
                req.taskName || '',
                req.coins || 0,
                req.moneyAmount || 0,
                req.status || 'pending',
                req.date || req.created_at || new Date()
            ]
        );
    }
}

async function syncFriends(client, data, actingChildId) {
    if (!Array.isArray(data.friends) || !actingChildId) return;

    await client.query('DELETE FROM friends WHERE child_id = $1', [actingChildId]);
    for (const friendItem of data.friends) {
        const friendChildId = typeof friendItem === 'object' ? friendItem.friendChildId : null;
        if (!friendChildId) continue;
        await client.query(
            `INSERT INTO friends (child_id, friend_child_id) VALUES ($1, $2)
             ON CONFLICT DO NOTHING`,
            [actingChildId, friendChildId]
        );
    }
}

/**
 * Save family data (Scoped by actingChildId if provided)
 * @param {string} familyId 
 * @param {Object} data 
 * @param {number|null} actingChildId
 * @returns {Promise<boolean>}
 */
async function saveFamilyData(familyId, data, actingChildId = null) {
    const dbId = await familyRepository.getDbId(familyId);
    if (!dbId) return false;

    const defaultChildId = await resolveDefaultChildId(familyId, actingChildId);
    if (!defaultChildId) return false;

    const { deleteWhere, deleteParams } = buildDeleteScope(dbId, actingChildId);
    const syncContext = {
        dbId,
        data,
        actingChildId,
        defaultChildId,
        deleteWhere,
        deleteParams
    };

    const client = await getClient();
    try {
        await client.query('BEGIN');
        await syncBalances(client, data, actingChildId);
        await syncTasks(client, syncContext);
        await syncShop(client, syncContext);
        await syncHistory(client, dbId, data, actingChildId, defaultChildId);
        await syncRequests(client, syncContext);
        await syncFriends(client, data, actingChildId);

        await client.query('COMMIT');
        return true;
    } catch (err) {
        await client.query('ROLLBACK');
        console.error('Error saving family data:', err.message);
        return false;
    } finally {
        client.release();
    }
}

async function updateBalance(familyId, balance, childId) {
    if (!childId) return false;
    const result = await query(
        'UPDATE children SET balance = $1 WHERE id = $2',
        [balance, childId]
    );
    return result.rowCount > 0;
}

// ... helper for adding request, history, etc must be updated to take childId

async function addHistoryEntry(familyId, entry) {
    const dbId = await familyRepository.getDbId(familyId);
    if (!dbId) return false;

    const childId = entry.childId;
    if (!childId) return false;

    const relatedId = entry.itemId || entry.taskId || entry.relatedId || null;
    const result = await query(
        `INSERT INTO history (family_id, child_id, external_id, type, amount, description, money_amount, related_id, created_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)`,
        [
            dbId,
            childId,
            entry.id || null,
            entry.type || 'unknown',
            entry.amount || 0,
            entry.description || '',
            entry.moneyAmount || 0,
            relatedId,
            entry.date || entry.timestamp || new Date()
        ]
    );
    return result.rowCount > 0;
}

async function addRequest(familyId, request) {
    const dbId = await familyRepository.getDbId(familyId);
    if (!dbId) return false;
    const childId = request.childId;
    if (!childId) return false;

    const result = await query(
        `INSERT INTO requests (family_id, child_id, external_id, request_type, task_id, item_id, task_name, coins, money_amount, status, created_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11) RETURNING id`,
        [
            dbId,
            childId,
            request.id || null,
            request.requestType || 'earn',
            request.taskId || null,
            request.itemId || null,
            request.taskName || '',
            request.coins || 0,
            request.moneyAmount || 0,
            request.status || 'pending',
            request.date || request.created_at || new Date()
        ]
    );
    return result.rows[0]?.id || null;
}

async function updateRequestStatus(requestId, status) {
    const result = await query(
        'UPDATE requests SET status = $1, updated_at = NOW() WHERE id = $2',
        [status, requestId]
    );
    return result.rowCount > 0;
}

async function addFriend(childId, friendChildId) {
    try {
        await query(
            'INSERT INTO friends (child_id, friend_child_id) VALUES ($1, $2)',
            [childId, friendChildId]
        );
        return true;
    } catch (err) {
        if (err.code === '23505') {
            return false;
        }
        throw err;
    }
}

async function getFriendsData(familyId, childId) {
    // If childId provided, return friends of that child
    // If familyId provided, return all?
    // Let's implement getting friends for a specific child
    if (!childId) return [];

    // We need balance of friend. Friend is a child.
    const result = await query(
        `SELECT fr.friend_child_id, c.name, c.balance
         FROM friends fr
         JOIN children c ON fr.friend_child_id = c.id
         WHERE fr.child_id = $1`,
        [childId]
    );

    return result.rows.map(row => ({
        id: row.friend_child_id, // This is ID of child table
        nickname: row.name,
        balance: row.balance
    }));
}

module.exports = {
    DEFAULT_FAMILY_DATA,
    getFamilyData,
    saveFamilyData,
    updateBalance,
    addHistoryEntry,
    addRequest,
    updateRequestStatus,
    addFriend,
    getFriendsData
};
