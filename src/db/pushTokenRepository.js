/** @file Push Token Repository PostgreSQL data access */
const { query } = require('./connection');

async function resolveFamilyDbId(familyId) {
    const result = await query('SELECT id FROM families WHERE family_id = $1', [familyId]);
    return result.rows[0]?.id || null;
}

async function ensureChildBelongsToFamily(childId, familyDbId) {
    if (!childId) return true;
    const result = await query(
        'SELECT id FROM children WHERE id = $1 AND family_id = $2',
        [childId, familyDbId]
    );
    return result.rowCount > 0;
}

async function upsertToken({ familyId, childId = null, role, token, platform = 'unknown' }) {
    const familyDbId = await resolveFamilyDbId(familyId);
    if (!familyDbId) return false;

    const safeChildId = childId ? parseInt(childId) : null;
    const isChildValid = await ensureChildBelongsToFamily(safeChildId, familyDbId);
    if (!isChildValid) return false;

    const result = await query(
        `INSERT INTO device_push_tokens (family_id, child_id, role, platform, token, is_active, updated_at, last_seen_at)
         VALUES ($1, $2, $3, $4, $5, TRUE, NOW(), NOW())
         ON CONFLICT (token) DO UPDATE SET
            family_id = EXCLUDED.family_id,
            child_id = EXCLUDED.child_id,
            role = EXCLUDED.role,
            platform = EXCLUDED.platform,
            is_active = TRUE,
            updated_at = NOW(),
            last_seen_at = NOW()`,
        [familyDbId, safeChildId, role, platform, token]
    );

    return result.rowCount > 0;
}

async function deactivateToken(familyId, token) {
    const result = await query(
        `UPDATE device_push_tokens t
         SET is_active = FALSE, updated_at = NOW()
         FROM families f
         WHERE f.family_id = $1
           AND t.family_id = f.id
           AND t.token = $2`,
        [familyId, token]
    );
    return result.rowCount > 0;
}

async function deactivateTokens(tokens) {
    if (!Array.isArray(tokens) || tokens.length === 0) return 0;

    const result = await query(
        `UPDATE device_push_tokens
         SET is_active = FALSE, updated_at = NOW()
         WHERE token = ANY($1)`,
        [tokens]
    );
    return result.rowCount;
}

async function getActiveTokens(familyId, options = {}) {
    const { roles = [], childId = null } = options;
    const params = [familyId];
    const conditions = [
        'f.family_id = $1',
        't.is_active = TRUE'
    ];

    if (Array.isArray(roles) && roles.length > 0) {
        params.push(roles);
        conditions.push(`t.role = ANY($${params.length})`);
    }

    if (childId) {
        params.push(parseInt(childId));
        conditions.push(`t.child_id = $${params.length}`);
    }

    const result = await query(
        `SELECT t.token, t.role, t.child_id AS "childId", t.platform
         FROM device_push_tokens t
         JOIN families f ON f.id = t.family_id
         WHERE ${conditions.join(' AND ')}`,
        params
    );

    return result.rows;
}

module.exports = {
    upsertToken,
    deactivateToken,
    deactivateTokens,
    getActiveTokens
};
