const { query } = require('./connection');

/**
 * Helper to map child row to object
 */
function mapChild(row) {
    if (!row) return null;
    return {
        id: row.id,
        name: row.name,
        token: row.token,
        balance: row.balance,
        monthlyLimit: row.monthly_limit,
        dailyCoinLimit: row.daily_coin_limit,
        familyId: row.family_id
    };
}

async function getChildren(familyDbId) {
    const result = await query('SELECT * FROM children WHERE family_id = $1 ORDER BY created_at', [familyDbId]);
    return result.rows.map(mapChild);
}

async function findChildById(childId) {
    const result = await query('SELECT * FROM children WHERE id = $1', [childId]);
    return mapChild(result.rows[0]);
}

async function findByChildToken(token) {
    const result = await query(
        `SELECT c.*, f.family_id as external_family_id, f.email, f.admin_password, f.is_blocked, f.created_at as family_created_at
         FROM children c
         JOIN families f ON c.family_id = f.id
         WHERE c.token = $1`,
        [token]
    );

    if (result.rows.length === 0) return null;
    const row = result.rows[0];
    return {
        family: {
            id: row.external_family_id,
            dbId: row.family_id,
            email: row.email,
            admin_password: row.admin_password,
            isBlocked: row.is_blocked,
            created_at: row.family_created_at
        },
        child: mapChild(row)
    };
}

async function createChild({ familyDbId, name, token, monthlyLimit = 10000 }) {
    if (token) {
        const check = await query('SELECT id FROM children WHERE token = $1', [token]);
        if (check.rows.length > 0) throw new Error('Token already in use');
    }

    const result = await query(
        'INSERT INTO children (family_id, name, token, monthly_limit) VALUES ($1, $2, $3, $4) RETURNING *',
        [familyDbId, name, token, monthlyLimit]
    );
    return mapChild(result.rows[0]);
}

async function updateChild(childId, data) {
    const fields = {
        name: data.name,
        token: data.token,
        balance: data.balance,
        monthly_limit: data.monthly_limit,
        daily_coin_limit: data.daily_coin_limit
    };

    const clauses = [];
    const vals = [];
    Object.keys(fields).forEach((key) => {
        if (fields[key] !== undefined) {
            clauses.push(`${key} = $${vals.length + 1}`);
            vals.push(fields[key]);
        }
    });

    if (clauses.length === 0) return true;

    vals.push(childId);
    const result = await query(
        `UPDATE children SET ${clauses.join(', ')} WHERE id = $${vals.length}`,
        vals
    );
    return result.rowCount > 0;
}

async function deleteChild(childId, familyDbId) {
    const result = await query('DELETE FROM children WHERE id = $1 AND family_id = $2', [childId, familyDbId]);
    return result.rowCount > 0;
}

async function isNicknameTaken(nickname, excludeFamilyDbId = null) {
    let sql = 'SELECT id FROM children WHERE LOWER(name) = LOWER($1)';
    const params = [nickname];

    if (excludeFamilyDbId) {
        sql += ' AND family_id != $2';
        params.push(excludeFamilyDbId);
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

module.exports = {
    getChildren,
    findChildById,
    findByChildToken,
    createChild,
    updateChild,
    deleteChild,
    isNicknameTaken,
    searchByNickname
};
