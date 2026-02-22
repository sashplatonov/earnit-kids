const { getAnalyticsData } = require('../services/familyService');
const { sendJSON } = require('../utils/controllerUtils');

async function handleAnalytics(ctx, req, res) {
    if (ctx.role !== 'admin' && ctx.role !== 'child') {
        return sendJSON(res, { error: 'Forbidden' }, 403);
    }
    const timeframe = ctx.urlObj.searchParams.get('timeframe') || 'month';
    let childId = ctx.urlObj.searchParams.get('childId') ? parseInt(ctx.urlObj.searchParams.get('childId')) : null;

    if (ctx.role === 'child') {
        childId = ctx.childId;
    }

    const data = await getAnalyticsData(ctx.familyId, childId, timeframe);
    sendJSON(res, data);
}

module.exports = {
    handleAnalytics
};
