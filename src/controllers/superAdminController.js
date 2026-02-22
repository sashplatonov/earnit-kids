const {
    loadFamilies, loadFamilyData, saveFamilies, regenerateChildToken,
    saveFamilyData
} = require('../services/familyService');
const { loadBaseData, saveBaseData } = require('../services/baseDataService');
const { createBackup, restoreBackup, copyToReserve, checkReserveDbConnection } = require('../services/backupService');
const parseBody = require('../middleware/body-parser');
const { sendJSON } = require('./authController');

async function getSuperFamiliesList() {
    const familiesData = await loadFamilies();
    const familyList = [];
    for (const [id, data] of Object.entries(familiesData.families)) {
        const familyData = await loadFamilyData(id);
        familyList.push({
            id,
            ...data,
            childrenCount: data.children ? data.children.length : 0,
            tasksCount: familyData.tasks ? familyData.tasks.length : 0,
            shopCount: familyData.shop ? familyData.shop.length : 0
        });
    }
    return familyList;
}

async function handleSuperFamilyData(url, method, req, res) {
    const match = url.match(/^\/api\/super\/family\/([^/]+)\/data$/);
    if (!match) return false;

    const familyId = match[1];
    if (method === 'GET') {
        const families = await loadFamilies();
        const familyInfo = families.families[familyId];
        if (!familyInfo) return sendJSON(res, { error: 'Not found' }, 404);
        return sendJSON(res, { familyId, familyInfo, data: await loadFamilyData(familyId) });
    }

    if (method === 'POST') {
        const body = await parseBody(req);
        const success = await saveFamilyData(familyId, body);
        return sendJSON(res, success ? { success: true } : { error: 'Failed' }, success ? 200 : 500);
    }

    return false;
}

async function handleSuperFamilyBlock(url, method, req, res) {
    const match = url.match(/^\/api\/super\/family\/([^/]+)\/block$/);
    if (!match || method !== 'POST') return false;

    const familyId = match[1];
    const body = await parseBody(req);
    const families = await loadFamilies();
    const family = families.families[familyId];
    if (!family) return sendJSON(res, { error: 'Failed' }, 404);

    family.isBlocked = body.isBlocked;
    const success = await saveFamilies(families);
    sendJSON(res, success ? { success: true } : { error: 'Failed' }, success ? 200 : 404);
}

// ... other super handlers can be moved here similarly
// For now, I'll stop here to avoid creating too many files, but keep an eye on file size

module.exports = {
    getSuperFamiliesList,
    handleSuperFamilyData,
    handleSuperFamilyBlock
    // and so on
};
