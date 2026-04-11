/** @file Family Service business services */
/**
 * Family Service - Business logic layer for family operations
 * Uses PostgreSQL database via repositories
 */

const crypto = require('crypto');
const familyRepository = require('../db/familyRepository');
const familyDataRepository = require('../db/familyDataRepository');
const pushService = require('./pushService');
const { createLogger } = require('../utils/logger');
const logger = createLogger('familyService');

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
                    isBlocked: data.isBlocked
                });
            }
        }
        return true;
    } catch (err) {
        logger.error({ err: err.message }, 'Bulk family save failed');
        return false;
    }
}

/**
 * Load family data (balance, tasks, shop, etc.)
 * @param {string} familyId 
 * @param {number|null} childId
 * @returns {Promise<Object>}
 */
async function loadFamilyData(familyId, childId = null) {
    return await familyDataRepository.getFamilyData(familyId, childId);
}

/**
 * Save family data
 * @param {string} familyId 
 * @param {Object} data 
 * @param {Object} [options] - Optional settings
 * @param {number|null} [options.childId] - The child performing the action (if any)
 * @param {string|null} [options.actingRole] - 'admin' or 'child'
 * @returns {Promise<boolean>}
 */
async function saveFamilyData(familyId, data, options = {}) {
    const { childId = null, actingRole = null } = options;
    if (!pushService.notifyFamilyChanges) {
        return await familyDataRepository.saveFamilyData(familyId, data, childId);
    }

    // To detect changes, we need before/after state
    const [beforeData, beforeChildren] = await Promise.all([
        familyDataRepository.getFamilyData(familyId, childId),
        familyRepository.getChildren(familyId)
    ]);

    const success = await familyDataRepository.saveFamilyData(familyId, data, childId);

    if (success) {
        // Fetch fresh state after save for comparison
        const [afterData, afterChildren] = await Promise.all([
            familyDataRepository.getFamilyData(familyId, childId),
            familyRepository.getChildren(familyId)
        ]);

        // Trigger push notifications in background
        void pushService.notifyFamilyChanges({
            familyId,
            beforeData,
            afterData,
            beforeChildren,
            afterChildren,
            actingRole,
            actingChildId: childId
        }).catch(err => logger.error({ err: err.message }, 'Failed to send push notifications'));
    }

    return success;
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
 * @param {number} childId
 * @param {Object} req 
 * @returns {Promise<string|null>}
 */
async function getChildLoginLink(familyId, childId, req) {
    const family = await familyRepository.findById(familyId);
    if (!family) return null;

    // Check if child belongs to family
    const child = family.children.find(c => c.id === parseInt(childId));
    if (!child || !child.token) return null;

    const protocol = req.headers['x-forwarded-proto'] || 'http';
    const host = req.headers['x-forwarded-host'] || req.headers.host;
    return `${protocol}://${host}/login-child/${child.token}`;
}

/**
 * Regenerate child token
 * @param {string} familyId 
 * @param {number} childId
 * @returns {Promise<boolean>}
 */
async function regenerateChildToken(familyId, childId) {
    // Verify ownership
    const family = await familyRepository.findById(familyId);
    if (!family) return false;
    const child = family.children.find(c => c.id === parseInt(childId));
    if (!child) return false;

    const newToken = crypto.randomBytes(32).toString('hex');
    return await familyRepository.updateChild(childId, { token: newToken }, family.dbId);
}

/**
 * Create a new child
 * @param {string} familyId 
 * @param {string} name 
 * @returns {Promise<Object>}
 */
async function addChild(familyId, name) {
    const token = crypto.randomBytes(32).toString('hex');
    const child = await familyRepository.createChild({ familyId, name, token });
    return { success: !!child, child };
}

/**
 * Delete a child
 * @param {string} familyId 
 * @param {number} childId 
 * @returns {Promise<boolean>}
 */
async function deleteChild(familyId, childId) {
    return await familyRepository.deleteChild(childId, familyId);
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
 * @param {number} childId
 * @param {string} nickname 
 * @returns {Promise<Object>}
 */
async function updateNickname(familyId, childId, nickname) {
    if (!nickname || nickname.length < 1) {
        return { success: false, error: 'Nickname too short' };
    }

    // Verify ownership
    const family = await familyRepository.findById(familyId);
    if (!family) return { success: false, error: 'Family not found' };
    const child = family.children.find(c => c.id === parseInt(childId));
    if (!child) return { success: false, error: 'Child not found' };

    if (await familyRepository.updateChild(childId, { name: nickname }, family.dbId)) {
        return { success: true };
    }
    return { success: false, error: 'Failed to save child settings' };
}

async function updateChildSettings(familyId, childId, settings) {
    const family = await familyRepository.findById(familyId);
    if (!family) return { success: false, error: 'Family not found' };
    const child = family.children.find(c => c.id === parseInt(childId));
    if (!child) return { success: false, error: 'Child not found' };

    const updateData = {};
    if (settings.monthly_limit !== undefined) updateData.monthly_limit = settings.monthly_limit;
    if (settings.daily_coin_limit !== undefined) updateData.daily_coin_limit = settings.daily_coin_limit;
    if (settings.name) updateData.name = settings.name;

    if (await familyRepository.updateChild(childId, updateData, family.dbId)) {
        return { success: true };
    }
    return { success: false, error: 'Failed to update' };
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
 * Add friend (Child to Child)
 * @param {number} childId 
 * @param {number} friendChildId 
 * @returns {Promise<Object>}
 */
async function addFriend(familyId, childId, friendChildId) {
    if (childId === friendChildId) {
        return { success: false, error: 'Cannot add yourself' };
    }

    const friendCheck = await familyRepository.findChildById(friendChildId);
    if (!friendCheck) {
        return { success: false, error: 'User not found' };
    }

    if (await familyDataRepository.addFriend(familyId, childId, friendChildId)) {
        return { success: true };
    }
    return { success: false, error: 'Already friends or failed to add' };
}

/**
 * Get friends data with balances
 * @param {string} familyId 
 * @param {number} childId
 * @returns {Promise<Array>}
 */
async function getFriendsData(familyId, childId) {
    return await familyDataRepository.getFriendsData(familyId, childId);
}

/**
 * Get analytics data for a family/child
 * @param {string} familyId
 * @param {number|null} childId
 * @param {string} timeframe
 * @returns {Promise<Object>}
 */
async function getAnalyticsData(familyId, childId, timeframe) {
    return await familyDataRepository.getAnalyticsData(familyId, childId, timeframe);
}

async function getPaginatedHistory(familyId, childId, pagination) {
    return await familyDataRepository.getPaginatedHistory(familyId, childId, pagination);
}

async function getPaginatedRequests(familyId, childId, pagination) {
    return await familyDataRepository.getPaginatedRequests(familyId, childId, pagination);
}

module.exports = {
    loadFamilies,
    saveFamilies,
    loadFamilyData,
    saveFamilyData,
    updateLastActivity,
    getChildLoginLink,
    regenerateChildToken,
    findFamilyByEmail,
    updateNickname,
    searchByNickname,
    addFriend,
    getFriendsData,
    getAnalyticsData,
    getPaginatedHistory,
    getPaginatedRequests,
    updateChildSettings,
    addChild,
    deleteChild,
    DEFAULT_FAMILY_DATA
};
