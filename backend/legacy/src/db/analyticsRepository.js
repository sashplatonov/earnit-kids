/** @file Analytics Repository PostgreSQL data access */
/**
 * Analytics Repository - Database access for analytics and reporting
 */

const { query } = require('./connection');
const familyRepository = require('./familyRepository');
const cache = require('../utils/Cache');

function getInterval(tf) {
    if (tf === 'week') return '7 days';
    if (tf === 'year') return '1 year';
    return '1 month';
}

async function fetchTrends(dbId, timeframe, childId) {
    const interval = getInterval(timeframe);
    const f = childId ? ' AND child_id = $3' : '';
    const p = childId ? [dbId, interval, childId] : [dbId, interval];

    const sql = `
        SELECT date_trunc('day', created_at) as date, 
               SUM(CASE WHEN type = 'earn' THEN amount ELSE 0 END) as earned, 
               SUM(CASE WHEN type = 'spend' THEN amount ELSE 0 END) as spent 
        FROM history
        WHERE family_id = $1 AND created_at >= NOW() - $2::interval${f}
        GROUP BY 1 ORDER BY 1 ASC
    `;
    const res = await query(sql, p);
    return res.rows.map(r => ({
        date: r.date,
        earned: parseInt(r.earned || 0),
        spent: parseInt(r.spent || 0)
    }));
}

async function fetchComparison(dbId, timeframe, childId) {
    const interval = getInterval(timeframe);
    const f = childId ? ' AND child_id = $3' : '';
    const p = childId ? [dbId, interval, childId] : [dbId, interval];

    // Summary for PREVIOUS period of same duration
    const sql = `
        SELECT SUM(CASE WHEN type='earn' THEN amount ELSE 0 END) as e, 
               SUM(CASE WHEN type='spend' THEN amount ELSE 0 END) as s 
        FROM history 
        WHERE family_id=$1 
          AND created_at < NOW() - $2::interval 
          AND created_at >= NOW() - ($2::interval * 2)${f}`;
    const res = await query(sql, p);
    const s = res.rows[0];
    return {
        totalEarned: parseInt(s.e || 0),
        totalSpent: parseInt(s.s || 0),
        netChange: parseInt((s.e || 0) - (s.s || 0))
    };
}

async function fetchRecommendations(dbId, childId) {
    const p = childId ? [dbId, childId] : [dbId];
    const childFilter = childId ? ' AND t.child_id = $2' : '';
    const historyFilter = childId ? ' AND h.child_id = $2' : '';

    const sql = `
        SELECT t.name, t.coins, COUNT(h.id) as completion_count
        FROM tasks t
        LEFT JOIN history h ON t.task_id = h.related_id AND h.type = 'earn' AND h.created_at >= NOW() - interval '30 days'${historyFilter}
        WHERE t.family_id = $1 AND t.is_deleted = false${childFilter}
        GROUP BY t.task_id, t.name, t.coins
        ORDER BY completion_count ASC, t.coins DESC
        LIMIT 3`;

    const res = await query(sql, p);
    return res.rows.map(r => ({
        name: r.name,
        coins: r.coins,
        reason: r.completion_count === '0' ? 'Давно не выполнялось' : 'Стоит повторить'
    }));
}

async function fetchAnalyticsRaw(dbId, timeframe, childId) {
    const interval = getInterval(timeframe);
    const summaryFilter = childId ? ' AND child_id = $3' : '';
    const detailFilter = childId ? ' AND h.child_id = $3' : '';
    const p = childId ? [dbId, interval, childId] : [dbId, interval];

    const qs = `SELECT SUM(CASE WHEN type='earn' THEN amount ELSE 0 END) as e, SUM(CASE WHEN type='spend' THEN amount ELSE 0 END) as s FROM history WHERE family_id=$1 AND created_at >= NOW() - $2::interval${summaryFilter}`;
    const qt = `
        SELECT COALESCE(t.name, h.description, 'Задание') as n, SUM(h.amount) as c, COUNT(*) as ct 
        FROM history h 
        LEFT JOIN tasks t ON h.related_id = t.task_id AND h.family_id = t.family_id
        WHERE h.family_id=$1 AND h.type='earn' AND h.created_at >= NOW() - $2::interval${detailFilter} 
        GROUP BY n ORDER BY c DESC`;
    const qi = `
        SELECT COALESCE(i.name, h.description, 'Товар') as n, SUM(h.amount) as c, COUNT(*) as ct 
        FROM history h 
        LEFT JOIN shop_items i ON h.related_id = i.item_id AND h.family_id = i.family_id
        WHERE h.family_id=$1 AND h.type='spend' AND h.created_at >= NOW() - $2::interval${detailFilter} 
        GROUP BY n ORDER BY c DESC
    `;

    return await Promise.all([query(qs, p), query(qt, p), query(qi, p)]);
}

async function getAnalyticsData(familyId, childId, timeframe = 'month') {
    const cacheKey = `analytics:${familyId}:${childId || 'all'}:${timeframe}`;
    const cached = cache.get(cacheKey);
    if (cached) return cached;

    const dbId = await familyRepository.getDbId(familyId);
    if (!dbId) return { summary: {}, topTasks: [], topItems: [], trends: [], comparison: {}, recommendations: [] };

    const [[sr, tr, ir], trends, comparison, recommendations] = await Promise.all([
        fetchAnalyticsRaw(dbId, timeframe, childId),
        fetchTrends(dbId, timeframe, childId),
        fetchComparison(dbId, timeframe, childId),
        fetchRecommendations(dbId, childId)
    ]);

    const s = sr.rows[0];
    const mapR = r => ({ name: r.n, coins: parseInt(r.c), count: parseInt(r.ct) });

    const result = {
        summary: {
            totalEarned: parseInt(s.e || 0),
            totalSpent: parseInt(s.s || 0),
            netChange: parseInt((s.e || 0) - (s.s || 0))
        },
        topTasks: tr.rows.map(mapR),
        topItems: ir.rows.map(mapR),
        trends,
        comparison,
        recommendations
    };

    cache.set(cacheKey, result, 60000);
    return result;
}

module.exports = {
    getAnalyticsData
};
