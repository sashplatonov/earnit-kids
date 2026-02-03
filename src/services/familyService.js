const fs = require('fs');
const path = require('path');
const { FAMILIES_FILE, FAMILIES_DATA_DIR, DATA_DIR } = require('../config');

const DEFAULT_FAMILY_DATA = {
    balance: 0,
    tasks: [],
    shop: [],
    history: [],
    requests: [],
    friends: []
};

function ensureDataDir() {
    if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
    if (!fs.existsSync(FAMILIES_DATA_DIR)) fs.mkdirSync(FAMILIES_DATA_DIR, { recursive: true });
}

function loadFamilies() {
    ensureDataDir();
    let data = { families: {} };

    if (fs.existsSync(FAMILIES_FILE)) {
        try {
            const content = fs.readFileSync(FAMILIES_FILE, 'utf8');
            data = JSON.parse(content);
        } catch (err) {
            console.error('Error loading families:', err.message);
        }
    }

    // Ensure super_admin is always set from env or existing data or defaults
    data.super_admin = {
        email: process.env.SUPER_ADMIN_EMAIL || (data.super_admin ? data.super_admin.email : 'admin@admin.com'),
        password: process.env.SUPER_ADMIN_PASSWORD || (data.super_admin ? data.super_admin.password : '000000')
    };

    return data;
}

function saveFamilies(familiesData) {
    ensureDataDir();
    try {
        fs.writeFileSync(FAMILIES_FILE, JSON.stringify(familiesData, null, 2), 'utf8');
        return true;
    } catch (err) {
        console.error('Error saving families:', err.message);
        return false;
    }
}

function loadFamilyData(familyId) {
    const familyFile = path.join(FAMILIES_DATA_DIR, `${familyId}.json`);
    try {
        if (fs.existsSync(familyFile)) {
            const content = fs.readFileSync(familyFile, 'utf8');
            return { ...DEFAULT_FAMILY_DATA, ...JSON.parse(content) };
        }
    } catch (err) {
        console.error(`Error loading family ${familyId} data:`, err.message);
    }
    return { ...DEFAULT_FAMILY_DATA };
}

function saveFamilyData(familyId, data) {
    ensureDataDir();
    const familyFile = path.join(FAMILIES_DATA_DIR, `${familyId}.json`);
    try {
        fs.writeFileSync(familyFile, JSON.stringify(data, null, 2), 'utf8');
        return true;
    } catch (err) {
        console.error(`Error saving family ${familyId} data:`, err.message);
        return false;
    }
}

function updateLastActivity(familyId) {
    const families = loadFamilies();
    if (families.families[familyId]) {
        families.families[familyId].last_activity = new Date().toISOString();
        saveFamilies(families);
    }
}

function getChildLoginLink(familyId, req) {
    const families = loadFamilies();
    const family = families.families[familyId];
    if (!family || !family.child_token) return null;

    const protocol = req.headers['x-forwarded-proto'] || 'http';
    const host = req.headers.host;
    return `${protocol}://${host}/login-child/${family.child_token}`;
}

function regenerateChildToken(familyId) {
    const families = loadFamilies();
    const crypto = require('crypto');
    if (families.families[familyId]) {
        families.families[familyId].child_token = crypto.randomBytes(32).toString('hex');
        saveFamilies(families);
        return true;
    }
    return false;
}

function updateFamilySettings(familyId, settings) {
    const families = loadFamilies();
    const family = families.families[familyId];
    if (!family) return { success: false, error: 'Not found' };
    if (settings.name) family.name = settings.name;
    if (settings.monthly_limit !== undefined) family.monthly_limit = parseInt(settings.monthly_limit);
    if (saveFamilies(families)) return { success: true };
    return { success: false, error: 'Save failed' };
}

function findFamilyByEmail(email) {
    const families = loadFamilies();

    // Check Super Admin
    if (families.super_admin && families.super_admin.email === email) {
        return {
            id: 'super_admin',
            isSuperAdmin: true,
            email: email,
            name: 'Super Admin'
        };
    }

    const entry = Object.entries(families.families).find(([id, data]) => data.email === email);
    if (entry) {
        return { id: entry[0], ...entry[1] };
    }
    return null;
}

function updateNickname(familyId, nickname) {
    if (!nickname || nickname.length < 3) return { success: false, error: 'Nickname too short' };
    const families = loadFamilies();

    // Check if nickname is unique
    const normalizedNickname = nickname.toLowerCase();
    const isTaken = Object.entries(families.families).some(([id, data]) =>
        id !== familyId && data.child_nickname && data.child_nickname.toLowerCase() === normalizedNickname
    );
    if (isTaken) return { success: false, error: 'Nickname already taken' };

    if (families.families[familyId]) {
        families.families[familyId].child_nickname = nickname;
        if (saveFamilies(families)) return { success: true };
    }
    return { success: false, error: 'Failed to save nickname' };
}

function searchByNickname(nickname) {
    if (!nickname || nickname.length < 3) return [];
    const families = loadFamilies();
    const normalized = nickname.toLowerCase();

    return Object.entries(families.families)
        .filter(([id, data]) => data.child_nickname && data.child_nickname.toLowerCase().includes(normalized))
        .map(([id, data]) => ({
            id,
            nickname: data.child_nickname
        }));
}

function addFriend(familyId, friendId) {
    if (familyId === friendId) return { success: false, error: 'Cannot add yourself' };

    const families = loadFamilies();
    if (!families.families[friendId]) return { success: false, error: 'User not found' };

    const data = loadFamilyData(familyId);
    if (!data.friends) data.friends = [];

    if (data.friends.includes(friendId)) return { success: false, error: 'Already friends' };

    data.friends.push(friendId);
    if (saveFamilyData(familyId, data)) return { success: true };
    return { success: false, error: 'Failed to add friend' };
}

function getFriendsData(familyId) {
    const data = loadFamilyData(familyId);
    const friends = data.friends || [];
    const families = loadFamilies();

    return friends.map(friendId => {
        const friendInfo = families.families[friendId];
        const friendData = loadFamilyData(friendId);
        return {
            id: friendId,
            nickname: friendInfo ? (friendInfo.child_nickname || 'Unknown') : 'Unknown',
            balance: friendData.balance || 0
        };
    });
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
