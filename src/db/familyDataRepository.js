/**
 * Family Data Repository - Database access layer for family data
 * (balance, tasks, shop items, history, requests, friends)
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
 * Get complete family data
 * @param {string} familyId 
 * @returns {Promise<Object>}
 */
async function getFamilyData(familyId) {
    const dbId = await familyRepository.getDbId(familyId);
    if (!dbId) return { ...DEFAULT_FAMILY_DATA };

    const [balanceResult, tasksResult, shopResult, historyResult, requestsResult, friendsResult] = await Promise.all([
        query('SELECT balance FROM family_data WHERE family_id = $1', [dbId]),
        query('SELECT * FROM tasks WHERE family_id = $1 ORDER BY created_at', [dbId]),
        query('SELECT * FROM shop_items WHERE family_id = $1 ORDER BY created_at', [dbId]),
        query('SELECT * FROM history WHERE family_id = $1 ORDER BY created_at DESC LIMIT 200', [dbId]),
        query('SELECT * FROM requests WHERE family_id = $1 ORDER BY created_at DESC', [dbId]),
        query(`SELECT f.family_id FROM friends fr 
               JOIN families f ON f.id = fr.friend_family_id 
               WHERE fr.family_id = $1`, [dbId])
    ]);

    return {
        balance: balanceResult.rows[0]?.balance || 0,
        tasks: tasksResult.rows.map(row => ({
            id: row.task_id,
            name: row.name,
            coins: row.coins,
            group: row.group_name,
            frequency: row.frequency,
            comment: row.comment,
            money_limit: row.money_limit
        })),
        shop: shopResult.rows.map(row => ({
            id: row.item_id,
            name: row.name,
            price: row.price,
            group: row.group_name,
            frequency: row.frequency,
            money_limit: row.money_limit
        })),
        history: historyResult.rows.map(row => {
            const entry = {
                id: row.external_id ? parseInt(row.external_id) : row.id,
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
            taskId: row.task_id ? parseInt(row.task_id) : null,
            taskName: row.task_name,
            coins: row.coins,
            status: row.status,
            date: row.created_at
        })),
        friends: friendsResult.rows.map(row => row.family_id)
    };
}

/**
 * Save complete family data
 * @param {string} familyId 
 * @param {Object} data 
 * @returns {Promise<boolean>}
 */
async function saveFamilyData(familyId, data) {
    const dbId = await familyRepository.getDbId(familyId);
    if (!dbId) return false;

    const client = await getClient();
    try {
        await client.query('BEGIN');

        // Update balance
        if (data.balance !== undefined) {
            await client.query(
                `INSERT INTO family_data (family_id, balance) VALUES ($1, $2)
                 ON CONFLICT (family_id) DO UPDATE SET balance = $2`,
                [dbId, data.balance]
            );
        }

        // Update tasks
        if (data.tasks !== undefined) {
            await client.query('DELETE FROM tasks WHERE family_id = $1', [dbId]);
            for (const task of data.tasks) {
                await client.query(
                    `INSERT INTO tasks (family_id, task_id, name, coins, group_name, frequency, comment, money_limit)
                     VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
                    [dbId, task.id, task.name, task.coins || 0, task.group || null,
                        task.frequency ? JSON.stringify(task.frequency) : null,
                        task.comment || null, task.money_limit || null]
                );
            }
        }

        // Update shop items
        if (data.shop !== undefined) {
            await client.query('DELETE FROM shop_items WHERE family_id = $1', [dbId]);
            for (const item of data.shop) {
                await client.query(
                    `INSERT INTO shop_items (family_id, item_id, name, price, group_name, frequency, money_limit)
                     VALUES ($1, $2, $3, $4, $5, $6, $7)`,
                    [dbId, item.id, item.name, item.price || 0, item.group || null,
                        item.frequency ? JSON.stringify(item.frequency) : null,
                        item.money_limit || null]
                );
            }
        }

        // Update history
        if (data.history !== undefined && Array.isArray(data.history)) {
            await client.query('DELETE FROM history WHERE family_id = $1', [dbId]);
            for (const entry of data.history) {
                const relatedId = entry.itemId || entry.taskId || entry.relatedId || null;
                await client.query(
                    `INSERT INTO history (family_id, external_id, type, amount, description, money_amount, related_id, created_at)
                     VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
                    [
                        dbId,
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

        // Update requests
        if (data.requests !== undefined && Array.isArray(data.requests)) {
            await client.query('DELETE FROM requests WHERE family_id = $1', [dbId]);
            for (const req of data.requests) {
                await client.query(
                    `INSERT INTO requests (family_id, external_id, task_id, task_name, coins, status, created_at)
                     VALUES ($1, $2, $3, $4, $5, $6, $7)`,
                    [
                        dbId,
                        req.id || null,
                        req.taskId || null,
                        req.taskName || '',
                        req.coins || 0,
                        req.status || 'pending',
                        req.date || req.created_at || new Date()
                    ]
                );
            }
        }

        // Update friends
        if (data.friends !== undefined && Array.isArray(data.friends)) {
            await client.query('DELETE FROM friends WHERE family_id = $1', [dbId]);
            for (const friendFamilyId of data.friends) {
                const friendDbId = await familyRepository.getDbId(friendFamilyId);
                if (friendDbId) {
                    await client.query(
                        `INSERT INTO friends (family_id, friend_family_id) VALUES ($1, $2)
                         ON CONFLICT DO NOTHING`,
                        [dbId, friendDbId]
                    );
                }
            }
        }

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

/**
 * Update balance only
 * @param {string} familyId 
 * @param {number} balance 
 * @returns {Promise<boolean>}
 */
async function updateBalance(familyId, balance) {
    const dbId = await familyRepository.getDbId(familyId);
    if (!dbId) return false;

    const result = await query(
        `INSERT INTO family_data (family_id, balance) VALUES ($1, $2)
         ON CONFLICT (family_id) DO UPDATE SET balance = $2`,
        [dbId, balance]
    );
    return result.rowCount > 0;
}

/**
 * Add history entry
 * @param {string} familyId 
 * @param {Object} entry 
 * @returns {Promise<boolean>}
 */
async function addHistoryEntry(familyId, entry) {
    const dbId = await familyRepository.getDbId(familyId);
    if (!dbId) return false;

    const relatedId = entry.itemId || entry.taskId || entry.relatedId || null;
    const result = await query(
        `INSERT INTO history (family_id, external_id, type, amount, description, money_amount, related_id, created_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
        [
            dbId,
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

/**
 * Add request
 * @param {string} familyId 
 * @param {Object} request 
 * @returns {Promise<boolean>}
 */
async function addRequest(familyId, request) {
    const dbId = await familyRepository.getDbId(familyId);
    if (!dbId) return false;

    const result = await query(
        `INSERT INTO requests (family_id, external_id, task_id, task_name, coins, status, created_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING id`,
        [
            dbId,
            request.id || null,
            request.taskId || null,
            request.taskName || '',
            request.coins || 0,
            request.status || 'pending',
            request.date || request.created_at || new Date()
        ]
    );
    return result.rows[0]?.id || null;
}

/**
 * Update request status
 * @param {number} requestId 
 * @param {string} status 
 * @returns {Promise<boolean>}
 */
async function updateRequestStatus(requestId, status) {
    const result = await query(
        'UPDATE requests SET status = $1, updated_at = NOW() WHERE id = $2',
        [status, requestId]
    );
    return result.rowCount > 0;
}

/**
 * Add friend
 * @param {string} familyId 
 * @param {string} friendFamilyId 
 * @returns {Promise<boolean>}
 */
async function addFriend(familyId, friendFamilyId) {
    const [dbId, friendDbId] = await Promise.all([
        familyRepository.getDbId(familyId),
        familyRepository.getDbId(friendFamilyId)
    ]);

    if (!dbId || !friendDbId) return false;

    try {
        await query(
            'INSERT INTO friends (family_id, friend_family_id) VALUES ($1, $2)',
            [dbId, friendDbId]
        );
        return true;
    } catch (err) {
        if (err.code === '23505') { // unique violation
            return false;
        }
        throw err;
    }
}

/**
 * Get friends data with balances and nicknames
 * @param {string} familyId 
 * @returns {Promise<Array>}
 */
async function getFriendsData(familyId) {
    const dbId = await familyRepository.getDbId(familyId);
    if (!dbId) return [];

    const result = await query(
        `SELECT f.family_id, f.child_nickname, fd.balance
         FROM friends fr
         JOIN families f ON f.id = fr.friend_family_id
         LEFT JOIN family_data fd ON fd.family_id = f.id
         WHERE fr.family_id = $1`,
        [dbId]
    );

    return result.rows.map(row => ({
        id: row.family_id,
        nickname: row.child_nickname || 'Unknown',
        balance: row.balance || 0
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
