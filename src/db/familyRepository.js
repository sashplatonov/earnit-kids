/** @file Family Repository PostgreSQL data access */
/**
 * Family Repository - Database access layer for families
 */

const { query, getClient } = require('./connection');
const childRepository = require('./childRepository');

/**
 * Helper to attach children to a family object
 */
async function attachChildren(family) {
    if (!family) return null;
    family.children = await childRepository.getChildren(family.dbId || family.id);
    return family;
}

/**
 * Find all families
 */
async function findAll() {
    const familiesResult = await query('SELECT * FROM families ORDER BY created_at DESC');

    const families = {};
    for (const row of familiesResult.rows) {
        const children = await childRepository.getChildren(row.id);
        families[row.family_id] = {
            familyId: row.family_id,
            email: row.email,
            admin_password: row.admin_password,
            isBlocked: row.is_blocked,
            isVerified: row.is_verified,
            monthly_limit: row.monthly_limit,
            created_at: row.created_at,
            last_activity: row.last_activity,
            children
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

async function findById(familyId) {
    const result = await query('SELECT * FROM families WHERE family_id = $1', [familyId]);
    if (result.rows.length === 0) return null;

    const row = result.rows[0];
    const family = {
        id: row.family_id,
        dbId: row.id,
        email: row.email,
        admin_password: row.admin_password,
        isBlocked: row.is_blocked,
        isVerified: row.is_verified,
        created_at: row.created_at,
        last_activity: row.last_activity
    };

    return await attachChildren(family);
}

async function findByEmail(email) {
    if (email === process.env.SUPER_ADMIN_EMAIL) {
        return { id: 'super_admin', isSuperAdmin: true, email: process.env.SUPER_ADMIN_EMAIL, password: process.env.SUPER_ADMIN_PASSWORD, name: 'Super Admin' };
    }

    const result = await query('SELECT * FROM families WHERE email = $1', [email]);
    if (result.rows.length === 0) return null;

    const row = result.rows[0];
    const family = {
        id: row.family_id,
        dbId: row.id,
        email: row.email,
        admin_password: row.admin_password,
        isBlocked: row.is_blocked,
        isVerified: row.is_verified,
        created_at: row.created_at,
        last_activity: row.last_activity
    };
    return await attachChildren(family);
}

async function findByChildToken(token) {
    const res = await childRepository.findByChildToken(token);
    if (!res) return null;

    const family = { ...res.family, currentChild: res.child };
    return await attachChildren(family);
}

async function create(data) {
    const client = await getClient();
    try {
        await client.query('BEGIN');
        const familyResult = await client.query(
        'INSERT INTO families (family_id, email, admin_password, is_verified, verification_token) VALUES ($1, $2, $3, $4, $5) RETURNING *',
        [data.family_id, data.email, data.admin_password, data.isVerified || false, data.verification_token]
        );
        const family = familyResult.rows[0];

        await childRepository.createChild({
            familyDbId: family.id,
            name: data.child_nickname || 'Child',
            token: data.child_token,
            monthlyLimit: data.monthly_limit || 10000
        });

        await client.query('COMMIT');
        return { success: true, familyId: data.family_id, dbId: family.id };
    } catch (err) {
        await client.query('ROLLBACK');
        throw err;
    } finally { client.release(); }
}

async function update(familyId, data) {
    const setClauses = [];
    const values = [];
    if (data.admin_password !== undefined) { setClauses.push(`admin_password = $${values.length + 1}`); values.push(data.admin_password); }
    if (data.is_blocked !== undefined) { setClauses.push(`is_blocked = $${values.length + 1}`); values.push(data.is_blocked); }

    if (setClauses.length === 0) return true;

    values.push(familyId);
    const result = await query(`UPDATE families SET ${setClauses.join(', ')} WHERE family_id = $${values.length}`, values);
    return result.rowCount > 0;
}

async function getDbId(familyId) {
    const result = await query('SELECT id FROM families WHERE family_id = $1', [familyId]);
    return result.rows[0]?.id || null;
}

async function deleteFamily(familyId) {
    const result = await query('DELETE FROM families WHERE family_id = $1', [familyId]);
    return result.rowCount > 0;
}

async function verifyFamily(familyId) {
    const result = await query('UPDATE families SET is_verified = TRUE, verification_token = NULL WHERE family_id = $1', [familyId]);
    return result.rowCount > 0;
}

async function findByVerificationToken(token) {
    const result = await query('SELECT * FROM families WHERE verification_token = $1', [token]);
    if (result.rows.length === 0) return null;
    const row = result.rows[0];
    return { id: row.family_id, dbId: row.id, email: row.email, verification_token: row.verification_token };
}

module.exports = {
    findAll,
    findById,
    findByEmail,
    findByChildToken,
    create,
    update,
    updateLastActivity: (fid) => query('UPDATE families SET last_activity = NOW() WHERE family_id = $1', [fid]).then(r => r.rowCount > 0),
    getDbId,
    deleteFamily,
    verifyFamily,
    findByVerificationToken,
    // Delegated to childRepository
    getChildren: (fid) => getDbId(fid).then(id => id ? childRepository.getChildren(id) : []),
    findChildById: childRepository.findChildById,
    createChild: ({ familyId, name, token, monthlyLimit }) => getDbId(familyId)
        .then(id => id ? childRepository.createChild({ familyDbId: id, name, token, monthlyLimit }) : null),
    updateChild: childRepository.updateChild,
    deleteChild: (cid, fid) => getDbId(fid).then(id => id ? childRepository.deleteChild(cid, id) : false),
    isNicknameTaken: (nick, exclFid) => (exclFid ? getDbId(exclFid) : Promise.resolve(null))
        .then(id => childRepository.isNicknameTaken(nick, id)),
    searchByNickname: childRepository.searchByNickname
};
