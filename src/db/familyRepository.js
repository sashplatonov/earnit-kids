/**
 * Family Repository - Database access layer for families
 * Updated to support multiple children
 */

const { query, getClient } = require('./connection');

/**
 * Helper to attach children to a family object
 */
async function attachChildren(family) {
    if (!family) return null;
    const childrenRes = await query('SELECT * FROM children WHERE family_id = $1 ORDER BY created_at', [family.dbId || family.id]);
    family.children = childrenRes.rows.map(c => ({
        id: c.id,
        name: c.name,
        token: c.token,
        balance: c.balance,
        balance: c.balance,
        monthlyLimit: c.monthly_limit,
        dailyCoinLimit: c.daily_coin_limit
    }));
    return family;
}

/**
 * Find all families
 * @returns {Promise<Object>} Object with families and super_admin
 */
async function findAll() {
    const familiesResult = await query('SELECT * FROM families ORDER BY created_at DESC');

    const families = {};
    for (const row of familiesResult.rows) {
        const childrenRes = await query('SELECT * FROM children WHERE family_id = $1', [row.id]);

        families[row.family_id] = {
            name: row.name,
            email: row.email,
            admin_password: row.admin_password,
            isBlocked: row.is_blocked,
            isVerified: row.is_verified,
            created_at: row.created_at,
            last_activity: row.last_activity,
            children: childrenRes.rows.map(c => ({
                id: c.id,
                name: c.name,
                token: c.token,
                balance: c.balance,
                monthlyLimit: c.monthly_limit,
                dailyCoinLimit: c.daily_coin_limit
            }))
        };
    }

    return {
        families,
        super_admin: {
            email: process.env.SUPER_ADMIN_EMAIL,
            password: process.env.SUPER_ADMIN_PASSWORD
        }
    };
}

/**
 * Find family by family_id string
 * @param {string} familyId 
 * @returns {Promise<Object|null>}
 */
async function findById(familyId) {
    const result = await query(
        'SELECT * FROM families WHERE family_id = $1',
        [familyId]
    );
    if (result.rows.length === 0) return null;

    const row = result.rows[0];
    const family = {
        id: row.family_id,
        dbId: row.id,
        name: row.name,
        email: row.email,
        admin_password: row.admin_password,
        isBlocked: row.is_blocked,
        isVerified: row.is_verified,
        created_at: row.created_at,
        last_activity: row.last_activity
    };

    return await attachChildren(family);
}

/**
 * Find family by email
 * @param {string} email 
 * @returns {Promise<Object|null>}
 */
async function findByEmail(email) {
    if (email === process.env.SUPER_ADMIN_EMAIL) {
        return {
            id: 'super_admin',
            isSuperAdmin: true,
            email: process.env.SUPER_ADMIN_EMAIL,
            password: process.env.SUPER_ADMIN_PASSWORD,
            name: 'Super Admin'
        };
    }

    const result = await query(
        'SELECT * FROM families WHERE email = $1',
        [email]
    );

    if (result.rows.length > 0) {
        const row = result.rows[0];
        const family = {
            id: row.family_id,
            dbId: row.id,
            name: row.name,
            email: row.email,
            admin_password: row.admin_password,
            isBlocked: row.is_blocked,
            isVerified: row.is_verified,
            created_at: row.created_at,
            last_activity: row.last_activity
        };
        return await attachChildren(family);
    }

    return null;
}

/**
 * Find family by child token
 * @param {string} token 
 * @returns {Promise<Object|null>} Returns object with family and specific child info
 */
async function findByChildToken(token) {
    const result = await query(
        `SELECT f.*, c.id as child_db_id, c.name as child_name, c.balance as child_balance, c.monthly_limit as child_limit, c.daily_coin_limit as child_daily_limit
         FROM children c
         JOIN families f ON c.family_id = f.id
         WHERE c.token = $1`,
        [token]
    );

    if (result.rows.length > 0) {
        const row = result.rows[0];
        const family = {
            id: row.family_id,
            dbId: row.id,
            name: row.name,
            email: row.email,
            admin_password: row.admin_password,
            isBlocked: row.is_blocked,
            created_at: row.created_at,
            last_activity: row.last_activity,
            currentChild: {
                id: row.child_db_id,
                name: row.child_name,
                balance: row.child_balance,
                name: row.child_name,
                balance: row.child_balance,
                monthlyLimit: row.child_limit,
                dailyCoinLimit: row.child_daily_limit, // Need to fetch this in query though
                token: token
            }
        };
        return await attachChildren(family);
    }

    return null;
}

/**
 * Create a new family (and default child)
 * @param {Object} data 
 * @returns {Promise<Object>}
 */
async function create(data) {
    const client = await getClient();
    try {
        await client.query('BEGIN');

        // Insert family
        const familyResult = await client.query(
            `INSERT INTO families (family_id, name, email, admin_password, is_verified, verification_token)
             VALUES ($1, $2, $3, $4, $5, $6)
             RETURNING *`,
            [
                data.family_id,
                data.name,
                data.email,
                data.admin_password,
                data.isVerified !== undefined ? data.isVerified : false,
                data.verification_token
            ]
        );

        const family = familyResult.rows[0];

        // Create Default Child
        const childName = data.child_nickname || 'Child';
        const childToken = data.child_token;

        await client.query(
            `INSERT INTO children (family_id, name, token, monthly_limit)
             VALUES ($1, $2, $3, $4)`,
            [family.id, childName, childToken, data.monthly_limit || 10000]
        );

        await client.query('COMMIT');
        return { success: true, familyId: data.family_id, dbId: family.id };
    } catch (err) {
        await client.query('ROLLBACK');
        throw err;
    } finally {
        client.release();
    }
}

/**
 * Update family settings
 * @param {string} familyId 
 * @param {Object} data 
 * @returns {Promise<boolean>}
 */
async function update(familyId, data) {
    const setClauses = [];
    const values = [];
    let paramIndex = 1;

    if (data.name !== undefined) {
        setClauses.push(`name = $${paramIndex++}`);
        values.push(data.name);
    }
    if (data.admin_password !== undefined) {
        setClauses.push(`admin_password = $${paramIndex++}`);
        values.push(data.admin_password);
    }
    if (data.is_blocked !== undefined) {
        setClauses.push(`is_blocked = $${paramIndex++}`);
        values.push(data.is_blocked);
    }
    // Note: Child updates (token, nickname, limit) handled via child-specific methods now

    if (setClauses.length === 0) return true;

    values.push(familyId);
    const result = await query(
        `UPDATE families SET ${setClauses.join(', ')} WHERE family_id = $${paramIndex}`,
        values
    );

    return result.rowCount > 0;
}

/**
 * Update last activity timestamp
 */
async function updateLastActivity(familyId) {
    const result = await query(
        'UPDATE families SET last_activity = NOW() WHERE family_id = $1',
        [familyId]
    );
    return result.rowCount > 0;
}

async function getDbId(familyId) {
    const result = await query(
        'SELECT id FROM families WHERE family_id = $1',
        [familyId]
    );
    return result.rows[0]?.id || null;
}

async function isNicknameTaken(nickname, excludeFamilyId = null) {
    // Only checks children now
    // NOTE: Nicknames are not globally unique anymore maybe? 
    // They were on 'families' table. Now on 'children'.
    // If we want globally unique nicknames for children? Or just unique within family?
    // User request: "One parent can have more than 1 child".
    // Usually nicknames are per family. But `isNicknameTaken` might be used for registration?
    // Registration only asks for Family Name.
    // So this is for Child Nickname edit.
    // If we want unique nicknames per family?
    // Let's assume unique per family for now.

    // UPDATE: The original checked GLOBALLY because it was on 'families' table.
    // I will change it to check globally in 'children' to avoid confusion if that's the desired behavior.
    // Or maybe just return false always if we don't care about global uniqueness anymore.
    // Let's keep it global for now to be safe.

    let sql = 'SELECT id FROM children WHERE LOWER(name) = LOWER($1)';
    const params = [nickname];

    // excludeFamilyId logic is tricky. If we just want to see if SOMEONE ELSE has it.
    // But families have multiple children.
    // Simplest: Check if ANY child has this name, excluding children of this family?
    // Or just check if name is taken by another child.

    // Let's keep it simple: Just check against all children.
    // Logic needs verify.
    // If excludeFamilyId is passed, maybe we want to ignore children of THAT family?
    // Or ignore a specific child ID?
    // Original code: `AND family_id != $2`.

    if (excludeFamilyId) {
        // Need to get DB ID of family
        const dbId = await getDbId(excludeFamilyId);
        if (dbId) {
            sql += ' AND family_id != $2';
            params.push(dbId);
        }
    }

    const result = await query(sql, params);
    return result.rows.length > 0;
}

async function searchByNickname(nickname) {
    const result = await query(
        `SELECT c.name, f.family_id
         FROM children c
         JOIN families f ON c.family_id = f.id
         WHERE LOWER(c.name) LIKE LOWER($1)`,
        [`%${nickname}%`]
    );

    return result.rows.map(row => ({
        id: row.family_id,
        nickname: row.name
    }));
}

async function deleteFamily(familyId) {
    const result = await query(
        'DELETE FROM families WHERE family_id = $1',
        [familyId]
    );
    return result.rowCount > 0;
}

async function verifyFamily(familyId) {
    const result = await query(
        'UPDATE families SET is_verified = TRUE, verification_token = NULL WHERE family_id = $1',
        [familyId]
    );
    return result.rowCount > 0;
}

async function findByVerificationToken(token) {
    const result = await query(
        'SELECT * FROM families WHERE verification_token = $1',
        [token]
    );
    if (result.rows.length > 0) {
        const row = result.rows[0];
        return {
            id: row.family_id,
            dbId: row.id,
            email: row.email,
            verification_token: row.verification_token
        };
    }
    return null;
}

/* Children Specific Methods */

async function getChildren(familyId) {
    const dbId = await getDbId(familyId);
    if (!dbId) return [];
    const result = await query('SELECT * FROM children WHERE family_id = $1 ORDER BY created_at', [dbId]);
    return result.rows;
}

async function findChildById(childId) {
    const result = await query('SELECT * FROM children WHERE id = $1', [childId]);
    return result.rows[0];
}

async function createChild(familyId, name, token, monthlyLimit = 10000) {
    const dbId = await getDbId(familyId);
    if (!dbId) return null;

    if (token) {
        const tokenCheck = await query('SELECT id FROM children WHERE token = $1', [token]);
        if (tokenCheck.rows.length > 0) throw new Error('Token already in use');
    }

    const result = await query(
        `INSERT INTO children (family_id, name, token, monthly_limit)
         VALUES ($1, $2, $3, $4) RETURNING *`,
        [dbId, name, token, monthlyLimit]
    );
    return result.rows[0];
}

async function updateChild(childId, data) {
    const clauses = [];
    const vals = [];
    let idx = 1;

    if (data.name !== undefined) { clauses.push(`name = $${idx++}`); vals.push(data.name); }
    if (data.token !== undefined) { clauses.push(`token = $${idx++}`); vals.push(data.token); }
    if (data.balance !== undefined) { clauses.push(`balance = $${idx++}`); vals.push(data.balance); }
    if (data.monthly_limit !== undefined) { clauses.push(`monthly_limit = $${idx++}`); vals.push(data.monthly_limit); }
    if (data.daily_coin_limit !== undefined) { clauses.push(`daily_coin_limit = $${idx++}`); vals.push(data.daily_coin_limit); }

    if (clauses.length === 0) return true;

    vals.push(childId);
    const result = await query(
        `UPDATE children SET ${clauses.join(', ')} WHERE id = $${idx}`,
        vals
    );
    return result.rowCount > 0;
}

async function deleteChild(childId, familyId) {
    const dbId = await getDbId(familyId);
    if (!dbId) return false;
    const result = await query('DELETE FROM children WHERE id = $1 AND family_id = $2', [childId, dbId]);
    return result.rowCount > 0;
}

module.exports = {
    findAll,
    findById,
    findByEmail,
    findByChildToken,
    create,
    update,
    updateLastActivity,
    getDbId,
    isNicknameTaken,
    searchByNickname,
    deleteFamily,
    verifyFamily,
    findByVerificationToken,
    getChildren,
    createChild,
    updateChild,
    deleteChild,
    findChildById
};
