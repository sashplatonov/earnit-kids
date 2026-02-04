/**
 * Family Repository - Database access layer for families
 */

const { query, getClient } = require('./connection');

/**
 * Find all families
 * @returns {Promise<Object>} Object with families and super_admin
 */
async function findAll() {
    const familiesResult = await query('SELECT * FROM families ORDER BY created_at DESC');

    const families = {};
    for (const row of familiesResult.rows) {
        families[row.family_id] = {
            name: row.name,
            email: row.email,
            admin_password: row.admin_password,
            child_token: row.child_token,
            monthly_limit: row.monthly_limit,
            child_nickname: row.child_nickname,
            isBlocked: row.is_blocked,
            isVerified: row.is_verified,
            created_at: row.created_at,
            last_activity: row.last_activity
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
    return result.rows[0] || null;
}

/**
 * Find family by email
 * @param {string} email 
 * @returns {Promise<Object|null>}
 */
async function findByEmail(email) {
    // Check super admin first (from environment variables)
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
        return {
            id: row.family_id,
            dbId: row.id,
            name: row.name,
            email: row.email,
            admin_password: row.admin_password,
            child_token: row.child_token,
            monthly_limit: row.monthly_limit,
            child_nickname: row.child_nickname,
            isBlocked: row.is_blocked,
            isVerified: row.is_verified,
            created_at: row.created_at,
            last_activity: row.last_activity
        };
    }

    return null;
}

/**
 * Find family by child token
 * @param {string} token 
 * @returns {Promise<Object|null>}
 */
async function findByChildToken(token) {
    const result = await query(
        'SELECT * FROM families WHERE child_token = $1',
        [token]
    );

    if (result.rows.length > 0) {
        const row = result.rows[0];
        return {
            id: row.family_id,
            dbId: row.id,
            name: row.name,
            email: row.email,
            admin_password: row.admin_password,
            child_token: row.child_token,
            monthly_limit: row.monthly_limit,
            child_nickname: row.child_nickname,
            isBlocked: row.is_blocked,
            created_at: row.created_at,
            last_activity: row.last_activity
        };
    }

    return null;
}

/**
 * Create a new family
 * @param {Object} data 
 * @returns {Promise<Object>}
 */
async function create(data) {
    const client = await getClient();
    try {
        await client.query('BEGIN');

        // Insert family
        const familyResult = await client.query(
            `INSERT INTO families (family_id, name, email, admin_password, child_token, monthly_limit, child_nickname, is_verified, verification_token)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
             RETURNING *`,
            [
                data.family_id,
                data.name,
                data.email,
                data.admin_password,
                data.child_token,
                data.monthly_limit || 10000,
                data.child_nickname || '',
                data.isVerified !== undefined ? data.isVerified : false, // is_verified
                data.verification_token // verification_token
            ]
        );

        const family = familyResult.rows[0];

        // Create family_data record
        await client.query(
            'INSERT INTO family_data (family_id, balance) VALUES ($1, $2)',
            [family.id, 0]
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
    if (data.child_token !== undefined) {
        setClauses.push(`child_token = $${paramIndex++}`);
        values.push(data.child_token);
    }
    if (data.monthly_limit !== undefined) {
        setClauses.push(`monthly_limit = $${paramIndex++}`);
        values.push(data.monthly_limit);
    }
    if (data.child_nickname !== undefined) {
        setClauses.push(`child_nickname = $${paramIndex++}`);
        values.push(data.child_nickname);
    }
    if (data.is_blocked !== undefined) {
        setClauses.push(`is_blocked = $${paramIndex++}`);
        values.push(data.is_blocked);
    }

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
 * @param {string} familyId 
 * @returns {Promise<boolean>}
 */
async function updateLastActivity(familyId) {
    const result = await query(
        'UPDATE families SET last_activity = NOW() WHERE family_id = $1',
        [familyId]
    );
    return result.rowCount > 0;
}

/**
 * Get database ID for a family
 * @param {string} familyId 
 * @returns {Promise<number|null>}
 */
async function getDbId(familyId) {
    const result = await query(
        'SELECT id FROM families WHERE family_id = $1',
        [familyId]
    );
    return result.rows[0]?.id || null;
}

/**
 * Check if nickname is taken
 * @param {string} nickname 
 * @param {string} excludeFamilyId 
 * @returns {Promise<boolean>}
 */
async function isNicknameTaken(nickname, excludeFamilyId = null) {
    let sql = 'SELECT id FROM families WHERE LOWER(child_nickname) = LOWER($1)';
    const params = [nickname];

    if (excludeFamilyId) {
        sql += ' AND family_id != $2';
        params.push(excludeFamilyId);
    }

    const result = await query(sql, params);
    return result.rows.length > 0;
}

/**
 * Search families by nickname
 * @param {string} nickname 
 * @returns {Promise<Array>}
 */
async function searchByNickname(nickname) {
    const result = await query(
        `SELECT family_id, child_nickname FROM families 
         WHERE child_nickname IS NOT NULL 
         AND LOWER(child_nickname) LIKE LOWER($1)`,
        [`%${nickname}%`]
    );

    return result.rows.map(row => ({
        id: row.family_id,
        nickname: row.child_nickname
    }));
}

/**
 * Delete a family
 * @param {string} familyId 
 * @returns {Promise<boolean>}
 */
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
    findByVerificationToken
};
