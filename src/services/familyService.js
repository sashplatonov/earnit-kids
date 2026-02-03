/**
 * Family Service - Business logic layer for family operations
 * Uses PostgreSQL database via repositories
 */

const crypto = require('crypto');
const familyRepository = require('../db/familyRepository');
const familyDataRepository = require('../db/familyDataRepository');

const DEFAULT_FAMILY_DATA = familyDataRepository.DEFAULT_FAMILY_DATA;

/**
 * Load all families (compatible with old JSON format)
 * @returns {Promise<Object>}
 */
async function loadFamilies() {
    return await familyRepository.findAll();
}

/**
 * Save families data (compatibility layer - updates individual families)
 * @param {Object} familiesData 
 * @returns {Promise<boolean>}
 */
async function saveFamilies(familiesData) {
    // This is a compatibility layer - in the new system, we update individual families
    // For bulk operations, iterate through families
    try {
        for (const [familyId, data] of Object.entries(familiesData.families || {})) {
            const existing = await familyRepository.findById(familyId);
            if (existing) {
                await familyRepository.update(familyId, {
                    name: data.name,
                    admin_password: data.admin_password,
                    child_token: data.child_token,
                    monthly_limit: data.monthly_limit,
                    child_nickname: data.child_nickname,
                    is_blocked: data.isBlocked
                });
            }
        }
        return true;
    } catch (err) {
        console.error('Error saving families:', err.message);
        return false;
    }
}

/**
 * Load family data (balance, tasks, shop, etc.)
 * @param {string} familyId 
 * @returns {Promise<Object>}
 */
async function loadFamilyData(familyId) {
    return await familyDataRepository.getFamilyData(familyId);
}

/**
 * Save family data
 * @param {string} familyId 
 * @param {Object} data 
 * @returns {Promise<boolean>}
 */
async function saveFamilyData(familyId, data) {
    return await familyDataRepository.saveFamilyData(familyId, data);
}

/**
 * Update last activity timestamp
 * @param {string} familyId 
 */
async function updateLastActivity(familyId) {
    await familyRepository.updateLastActivity(familyId);
}

/**
 * Get child login link
 * @param {string} familyId 
 * @param {Object} req 
 * @returns {Promise<string|null>}
 */
async function getChildLoginLink(familyId, req) {
    const family = await familyRepository.findById(familyId);
    if (!family || !family.child_token) return null;

    const protocol = req.headers['x-forwarded-proto'] || 'http';
    const host = req.headers.host;
    return `${protocol}://${host}/login-child/${family.child_token}`;
}

/**
 * Regenerate child token
 * @param {string} familyId 
 * @returns {Promise<boolean>}
 */
async function regenerateChildToken(familyId) {
    const newToken = crypto.randomBytes(32).toString('hex');
    return await familyRepository.update(familyId, { child_token: newToken });
}

/**
 * Update family settings
 * @param {string} familyId 
 * @param {Object} settings 
 * @returns {Promise<Object>}
 */
async function updateFamilySettings(familyId, settings) {
    const family = await familyRepository.findById(familyId);
    if (!family) return { success: false, error: 'Not found' };

    const updateData = {};
    if (settings.name) updateData.name = settings.name;
    if (settings.monthly_limit !== undefined) updateData.monthly_limit = parseInt(settings.monthly_limit);

    if (await familyRepository.update(familyId, updateData)) {
        return { success: true };
    }
    return { success: false, error: 'Save failed' };
}

/**
 * Find family by email
 * @param {string} email 
 * @returns {Promise<Object|null>}
 */
async function findFamilyByEmail(email) {
    return await familyRepository.findByEmail(email);
}

/**
 * Update child nickname
 * @param {string} familyId 
 * @param {string} nickname 
 * @returns {Promise<Object>}
 */
async function updateNickname(familyId, nickname) {
    if (!nickname || nickname.length < 3) {
        return { success: false, error: 'Nickname too short' };
    }

    const isTaken = await familyRepository.isNicknameTaken(nickname, familyId);
    if (isTaken) {
        return { success: false, error: 'Nickname already taken' };
    }

    if (await familyRepository.update(familyId, { child_nickname: nickname })) {
        return { success: true };
    }
    return { success: false, error: 'Failed to save nickname' };
}

/**
 * Search families by nickname
 * @param {string} nickname 
 * @returns {Promise<Array>}
 */
async function searchByNickname(nickname) {
    if (!nickname || nickname.length < 3) return [];
    return await familyRepository.searchByNickname(nickname);
}

/**
 * Add friend
 * @param {string} familyId 
 * @param {string} friendId 
 * @returns {Promise<Object>}
 */
async function addFriend(familyId, friendId) {
    if (familyId === friendId) {
        return { success: false, error: 'Cannot add yourself' };
    }

    const friend = await familyRepository.findById(friendId);
    if (!friend) {
        return { success: false, error: 'User not found' };
    }

    if (await familyDataRepository.addFriend(familyId, friendId)) {
        return { success: true };
    }
    return { success: false, error: 'Already friends or failed to add' };
}

/**
 * Get friends data with balances
 * @param {string} familyId 
 * @returns {Promise<Array>}
 */
async function getFriendsData(familyId) {
    return await familyDataRepository.getFriendsData(familyId);
}

module.exports = {
    loadFamilies,
    saveFamilies,
    loadFamilyData,
    saveFamilyData,
    updateLastActivity,
    getChildLoginLink,
    regenerateChildToken,
    updateFamilySettings,
    findFamilyByEmail,
    updateNickname,
    searchByNickname,
    addFriend,
    getFriendsData,
    DEFAULT_FAMILY_DATA
};
