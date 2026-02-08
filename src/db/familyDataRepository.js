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
            taskId: row.task_id ? parseInt(row.task_id) : null,
            taskName: row.task_name,
            coins: row.coins,
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

    // Consistency Check: If no actingChildId, and data doesn't contain childId info, we have a problem.
    // But we can fetch the "default" child for the family if needed?
    // Let's assume the Controller handles logic of "who is acting".

    let defaultChildId = actingChildId;
    if (!defaultChildId) {
        // Admin saving: Try to find a default child if not specified in items?
        // Or fetch first child.
        const children = await familyRepository.getChildren(familyId);
        if (children.length > 0) defaultChildId = children[0].id;
    }

    if (!defaultChildId) return false; // No children, cannot save data

    const client = await getClient();
    try {
        await client.query('BEGIN');

        // Update balance (if child is acting)
        if (data.balance !== undefined && actingChildId) {
            await client.query('UPDATE children SET balance = $1 WHERE id = $2', [data.balance, actingChildId]);
        }

        // Update balances for all children if provided (Admin view)
        if (data.children && Array.isArray(data.children) && !actingChildId) {
            for (const child of data.children) {
                if (child.id && child.balance !== undefined) {
                    await client.query('UPDATE children SET balance = $1 WHERE id = $2', [child.balance, child.id]);
                }
            }
        }

        // Scope deletion/updates
        let deleteWhere = 'WHERE family_id = $1';
        let deleteParams = [dbId];

        if (actingChildId) {
            deleteWhere += ' AND child_id = $2';
            deleteParams.push(actingChildId);
        }

        // Update tasks
        if (data.tasks !== undefined) {
            await client.query(`DELETE FROM tasks ${deleteWhere}`, deleteParams);
            for (const task of data.tasks) {
                const targetChildId = actingChildId || task.childId || defaultChildId;
                await client.query(
                    `INSERT INTO tasks (family_id, child_id, task_id, name, coins, group_name, frequency, comment, money_limit)
                     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)`,
                    [dbId, targetChildId, task.id, task.name, task.coins || 0, task.group || null,
                        task.frequency ? JSON.stringify(task.frequency) : null,
                        task.comment || null, task.money_limit || null]
                );
            }
        }

        // Update shop items
        if (data.shop !== undefined) {
            await client.query(`DELETE FROM shop_items ${deleteWhere}`, deleteParams);
            for (const item of data.shop) {
                const targetChildId = actingChildId || item.childId || defaultChildId;
                await client.query(
                    `INSERT INTO shop_items (family_id, child_id, item_id, name, price, group_name, frequency, money_limit)
                     VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
                    [dbId, targetChildId, item.id, item.name, item.price || 0, item.group || null,
                        item.frequency ? JSON.stringify(item.frequency) : null,
                        item.money_limit || null]
                );
            }
        }

        // Update history
        if (data.history !== undefined && Array.isArray(data.history)) {
            // IF Admin saves, we ONLY delete the records that we are REPLACING.
            // Since UI sends only top 200, we shouldn't delete everything.
            // For now, let's delete only those with external_id present in the incoming list OR delete onlyActingChild's history.
            
            if (actingChildId) {
                await client.query('DELETE FROM history WHERE child_id = $1', [actingChildId]);
            } else {
                // Admin saving: We only delete records for the children that are in the history provided.
                // Or just delete the last 200? 
                // A better way: ONLY delete what we are about to re-insert if it has an ID.
                // But for simplicity and to match the "sync" model:
                // Let's delete history only for the child that is "targeted" in the UI (if we knew it).
                // Actually, let's look at the IDs.
                const historyIds = data.history.map(h => h.id).filter(id => id);
                if (historyIds.length > 0) {
                     // Delete only those that match the IDs we are sending (to avoid duplicates)
                     // and maybe the latest ones?
                     // BUT the UI might have deleted some items!
                     // OK, let's stick to deleting ALL history for the family ONLY IF the history list is "long enough"
                     // OR just delete history for the children mentioned in history list.
                     const targetChildIds = [...new Set(data.history.map(h => h.childId || defaultChildId))];
                     await client.query('DELETE FROM history WHERE family_id = $1 AND child_id = ANY($2)', [dbId, targetChildIds]);
                }
            }

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

        // Update requests
        if (data.requests !== undefined && Array.isArray(data.requests)) {
            await client.query(`DELETE FROM requests ${deleteWhere}`, deleteParams);
            for (const req of data.requests) {
                const targetChildId = actingChildId || req.childId || defaultChildId;
                await client.query(
                    `INSERT INTO requests (family_id, child_id, external_id, task_id, task_name, coins, status, created_at)
                     VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
                    [
                        dbId,
                        targetChildId,
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

        // Friends
        if (data.friends !== undefined && Array.isArray(data.friends) && actingChildId) {
            // Only sync friends for specific child for now.
            await client.query('DELETE FROM friends WHERE child_id = $1', [actingChildId]);
            for (const fItem of data.friends) {
                // fItem might be ID or object
                let friendChildId = typeof fItem === 'object' ? fItem.friendChildId : null;
                if (!friendChildId) continue;
                // We don't verify if friend exists in this bulk sync to be fast, but DB constraint will check
                await client.query(
                    `INSERT INTO friends (child_id, friend_child_id) VALUES ($1, $2)
                     ON CONFLICT DO NOTHING`,
                    [actingChildId, friendChildId]
                );
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
        `INSERT INTO requests (family_id, child_id, external_id, task_id, task_name, coins, status, created_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8) RETURNING id`,
        [
            dbId,
            childId,
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
