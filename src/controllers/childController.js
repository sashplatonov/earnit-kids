/** @file Child Controller REST controller helpers */
const {
    getChildLoginLink, regenerateChildToken, deleteChild, updateChildSettings
} = require('../services/familyService');
const parseBody = require('../middleware/body-parser');
const { sendJSON } = require('../utils/controllerUtils');
const websocket = require('../utils/websocket');

async function handleLinkGet({ ctx, req, res, targetChildId }) {
    if (ctx.role !== 'admin') return sendJSON(res, { error: 'Forbidden' }, 403);
    const link = await getChildLoginLink(ctx.familyId, targetChildId, req);
    sendJSON(res, { link });
}

async function handleTokenRegen({ ctx, req, res, targetChildId }) {
    if (ctx.role !== 'admin') return sendJSON(res, { error: 'Forbidden' }, 403);
    const success = await regenerateChildToken(ctx.familyId, targetChildId);
    if (!success) return sendJSON(res, { error: 'Failed' }, 400);
    const link = await getChildLoginLink(ctx.familyId, targetChildId, req);
    sendJSON(res, { success: true, link });
}

async function handleDeleteChild({ ctx, req, res, targetChildId }) {
    if (ctx.role !== 'admin') return sendJSON(res, { error: 'Forbidden' }, 403);
    const success = await deleteChild(ctx.familyId, targetChildId);
    if (success) {
        websocket.notifyFamily(ctx.familyId, 'CHILD_DELETED', { childId: targetChildId });
    }
    sendJSON(res, success ? { success: true } : { error: 'Failed' }, success ? 200 : 400);
}

async function handleUpdateSettings({ ctx, req, res, targetChildId }) {
    if (ctx.role !== 'admin') return sendJSON(res, { error: 'Forbidden' }, 403);
    const body = await parseBody(req);
    const result = await updateChildSettings(ctx.familyId, targetChildId, body);
    if (result.success) {
        websocket.notifyFamily(ctx.familyId, 'CHILD_UPDATED', { childId: targetChildId });
    }
    sendJSON(res, result, result.success ? 200 : 400);
}

module.exports = {
    handleLinkGet,
    handleTokenRegen,
    handleDeleteChild,
    handleUpdateSettings
};
