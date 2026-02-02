const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const DATA_DIR = path.join(__dirname, '../data');
const FAMILIES_FILE = path.join(DATA_DIR, 'families.json');
const FAMILIES_DATA_DIR = path.join(DATA_DIR, 'families');

const DEFAULT_FAMILY_DATA = {
    balance: 0,
    tasks: [],
    shop: [],
    history: [],
    requests: []
};

// Ensure data directories exist
function ensureDataDir() {
    if (!fs.existsSync(DATA_DIR)) {
        fs.mkdirSync(DATA_DIR, { recursive: true });
    }
    if (!fs.existsSync(FAMILIES_DATA_DIR)) {
        fs.mkdirSync(FAMILIES_DATA_DIR, { recursive: true });
    }
}



// Load families registry
function loadFamilies() {
    ensureDataDir();
    let data;
    try {
        if (fs.existsSync(FAMILIES_FILE)) {
            const content = fs.readFileSync(FAMILIES_FILE, 'utf8');
            data = JSON.parse(content);
        }
    } catch (err) {
        console.error('Error loading families:', err.message);
    }

    if (!data) {
        data = {
            families: {}
        };
    }

    // Use environment variables for super admin, or fallback to file, or default
    data.super_admin = {
        email: process.env.SUPER_ADMIN_EMAIL || (data.super_admin ? data.super_admin.email : 'admin@coinshop.com'),
        password: process.env.SUPER_ADMIN_PASSWORD || (data.super_admin ? data.super_admin.password : '000000')
    };

    // Migration: ensure all families have a child_token
    let needsSave = false;
    if (data.families) {
        Object.values(data.families).forEach(family => {
            if (!family.child_token) {
                family.child_token = crypto.randomBytes(32).toString('hex');
                needsSave = true;
            }
        });
    }

    if (needsSave) {
        saveFamilies(data);
    }

    return data;
}

// Save families registry
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

// Validate Password Policy
function isValidPassword(password) {
    if (!password || password.length < 6) return false;
    // Check if all characters are identical
    const firstChar = password[0];
    const allSame = password.split('').every(char => char === firstChar);
    if (allSame) return false;
    return true;
}

// Authenticate user
function authenticateUser(email, password) {
    const families = loadFamilies();

    // Check Super Admin
    if (families.super_admin && families.super_admin.email === email) {
        if (families.super_admin.password === password) {
            return {
                success: true,
                role: 'super_admin',
                familyName: 'Super Admin',
                familyId: null
            };
        } else {
            return { success: false, error: 'Неверный пароль' };
        }
    }

    // Find family by email
    const entry = Object.entries(families.families).find(([id, data]) => data.email === email);

    if (entry) {
        const [familyId, data] = entry;

        if (data.isBlocked) {
            return { success: false, error: 'Аккаунт заблокирован. Обратитесь к администратору.' };
        }

        if (data.admin_password === password) {
            return {
                success: true,
                role: 'admin',
                familyName: data.name,
                familyId: familyId
            };
        }

        if (data.child_password === password) {
            return {
                success: true,
                role: 'child',
                familyName: data.name,
                familyId: familyId
            };
        }

        return { success: false, error: 'Неверный пароль' };
    }

    return { success: false, error: 'Пользователь не найден' };
}

// Authenticate child by magic token
function authenticateChildByToken(token) {
    if (!token) return { success: false, error: 'Токен отсутствует' };

    const families = loadFamilies();
    const entry = Object.entries(families.families).find(([id, data]) => data.child_token === token);

    if (entry) {
        const [familyId, data] = entry;

        if (data.isBlocked) {
            return { success: false, error: 'Аккаунт заблокирован. Обратитесь к администратору.' };
        }

        return {
            success: true,
            role: 'child',
            familyName: data.name,
            familyId: familyId,
            email: data.email
        };
    }

    return { success: false, error: 'Неверная ссылка или токен' };
}

// Find family by Email
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

// Find family by ID (moved from findFamilyByPin concept)
function findFamilyById(familyId) {
    const families = loadFamilies();
    const family = families.families[familyId];
    if (family) {
        return { id: familyId, ...family };
    }
    return null;
}

// Load data for a specific family
function loadFamilyData(familyId) {
    ensureDataDir();
    const familyFile = path.join(FAMILIES_DATA_DIR, `${familyId}.json`);
    // Fallback for old files if they exist (migration-friendly)
    const oldFamilyFile = path.join(DATA_DIR, `family_${familyId}.json`);
    try {
        if (fs.existsSync(familyFile)) {
            const content = fs.readFileSync(familyFile, 'utf8');
            return { ...DEFAULT_FAMILY_DATA, ...JSON.parse(content) };
        } else if (fs.existsSync(oldFamilyFile)) {
            const content = fs.readFileSync(oldFamilyFile, 'utf8');
            return { ...DEFAULT_FAMILY_DATA, ...JSON.parse(content) };
        }
    } catch (err) {
        console.error(`Error loading family ${familyId} data:`, err.message);
    }
    return { ...DEFAULT_FAMILY_DATA };
}

// Save data for a specific family
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

// Register a new family
function registerFamily(familyName, email, adminPassword, childPassword) {
    const families = loadFamilies();

    // Check if Email already exists
    const emailExists = Object.values(families.families).some(f => f.email === email);
    if (emailExists) {
        return { success: false, error: 'Email уже зарегистрирован' };
    }

    // Validate Password length and complexity
    if (!isValidPassword(adminPassword)) {
        return { success: false, error: 'Пароль родителя должен быть минимум 6 символов и не все одинаковые' };
    }

    // If child password is not provided, generate a random one (it won't be used with Magic Links)
    if (!childPassword) {
        childPassword = crypto.randomBytes(8).toString('hex');
    } else if (!isValidPassword(childPassword)) {
        return { success: false, error: 'Пароль ребенка должен быть минимум 6 символов и не все одинаковые' };
    }

    if (adminPassword === childPassword) {
        return { success: false, error: 'Пароли родителя и ребёнка должны отличаться' };
    }

    const now = new Date();
    const dateStr = now.toISOString().split('T')[0].replace(/-/g, '');
    const timeStr = now.toTimeString().split(' ')[0].replace(/:/g, '');
    const sanitizedEmail = email.replace(/[^a-zA-Z0-9]/g, '_');
    const familyId = `${sanitizedEmail}_${dateStr}_${timeStr}`;

    // Create family entry
    families.families[familyId] = {
        name: familyName || `Шоп ${familyId}`,
        email: email,
        created_at: now.toISOString(),
        admin_password: adminPassword,
        child_password: childPassword,
        child_token: crypto.randomBytes(32).toString('hex'),
        monthly_limit: 2000 // Default monthly limit
    };

    if (saveFamilies(families)) {
        // Create default family data
        let familyData = { ...DEFAULT_FAMILY_DATA };
        saveFamilyData(familyId, familyData);
        return { success: true, familyId };
    }

    return { success: false, error: 'Ошибка сохранения' };
}

// Get child login link
function getChildLoginLink(familyId, req) {
    const families = loadFamilies();
    const family = families.families[familyId];
    if (!family || !family.child_token) return null;

    const protocol = req.headers['x-forwarded-proto'] || 'http';
    const host = req.headers.host;
    return `${protocol}://${host}/login-child/${family.child_token}`;
}

// Regenerate child token
function regenerateChildToken(familyId) {
    const families = loadFamilies();
    if (families.families[familyId]) {
        families.families[familyId].child_token = crypto.randomBytes(32).toString('hex');
        saveFamilies(families);
        return true;
    }
    return false;
}

// Change Password for a family member
function changePassword(familyId, role, oldPassword, newPassword) {
    const families = loadFamilies();
    const family = families.families[familyId];

    if (!family) {
        return { success: false, error: 'Семья не найдена' };
    }

    // Check old password
    const currentPass = role === 'admin' ? family.admin_password : family.child_password;
    if (currentPass !== oldPassword) {
        return { success: false, error: 'Старый пароль неверен' };
    }

    // Validate new password
    if (!isValidPassword(newPassword)) {
        return { success: false, error: 'Новый пароль должен быть минимум 6 символов и не все одинаковые' };
    }

    // Check conflict with other role
    const otherPass = role === 'admin' ? family.child_password : family.admin_password;
    if (newPassword === otherPass) {
        return { success: false, error: 'Пароль не может совпадать с паролем другого члена семьи' };
    }

    // Update password
    if (role === 'admin') {
        family.admin_password = newPassword;
    } else {
        family.child_password = newPassword;
    }

    if (saveFamilies(families)) {
        return { success: true };
    }

    return { success: false, error: 'Ошибка сохранения' };
}

// Block/Unblock Family
function toggleFamilyBlock(familyId, isBlocked) {
    const families = loadFamilies();
    const family = families.families[familyId];

    if (!family) {
        return { success: false, error: 'Семья не найдена' };
    }

    family.isBlocked = isBlocked;

    if (saveFamilies(families)) {
        return { success: true };
    }
    return { success: false, error: 'Ошибка сохранения' };
}

// Update Family Name
function updateFamilyName(familyId, newName) {
    const families = loadFamilies();
    const family = families.families[familyId];

    if (!family) {
        return { success: false, error: 'Семья не найдена' };
    }

    family.name = newName;

    if (saveFamilies(families)) {
        return { success: true };
    }
    return { success: false, error: 'Ошибка сохранения' };
}

// Update Family Settings
function updateFamilySettings(familyId, settings) {
    const families = loadFamilies();
    const family = families.families[familyId];

    if (!family) {
        return { success: false, error: 'Семья не найдена' };
    }

    if (settings.name) family.name = settings.name;
    if (settings.monthly_limit !== undefined) family.monthly_limit = parseInt(settings.monthly_limit);

    if (saveFamilies(families)) {
        return { success: true };
    }
    return { success: false, error: 'Ошибка сохранения' };
}

const BASE_DATA_FILE = path.join(DATA_DIR, 'baseData.json');

// Load base data
function loadBaseData() {
    ensureDataDir();
    try {
        if (fs.existsSync(BASE_DATA_FILE)) {
            const content = fs.readFileSync(BASE_DATA_FILE, 'utf8');
            return JSON.parse(content);
        }
    } catch (err) {
        console.error('Error loading base data:', err.message);
    }
    return { tasks: [], products: [] };
}

// Save base data
function saveBaseData(data) {
    ensureDataDir();
    try {
        fs.writeFileSync(BASE_DATA_FILE, JSON.stringify(data, null, 2), 'utf8');
        return true;
    } catch (err) {
        console.error('Error saving base data:', err.message);
        return false;
    }
}

// Update Last Activity
function updateLastActivity(familyId) {
    const families = loadFamilies();
    const family = families.families[familyId];
    if (family) {
        family.last_activity = new Date().toISOString();
        saveFamilies(families);
    }
}

// Recover Password
async function recoverPassword(email) {
    const families = loadFamilies();

    // Check if email belongs to super admin
    if (families.super_admin && families.super_admin.email === email) {
        const { sendEmail } = require('./emailService');
        const subject = 'Восстановление пароля - Монетки (Super Admin)';
        const text = `Здравствуйте!\n\nВы запросили восстановление пароля для Super Admin.\n\nВаш пароль: ${families.super_admin.password}\n\nС уважением,\nКоманда Магазина Монеток`;
        const html = `<h2>Восстановление пароля</h2><p>Здравствуйте!</p><p>Вы запросили восстановление пароля для <b>Super Admin</b>.</p><p>Ваш пароль: <b>${families.super_admin.password}</b></p><br><p>С уважением,<br>Команда Магазина Монеток</p>`;

        return await sendEmail({ to: email, subject, text, html });
    }

    // Find family by email
    const entry = Object.entries(families.families).find(([id, data]) => data.email === email);
    if (!entry) {
        return { success: false, error: 'Пользователь с таким Email не найден' };
    }

    const [familyId, data] = entry;
    const { sendEmail } = require('./emailService');
    const subject = 'Восстановление пароля - Монетки';
    const text = `Здравствуйте!\n\nВы запросили восстановление пароля для вашего магазина "${data.name}".\n\nПароль администратора: ${data.admin_password}\n\nС уважением,\nКоманда Магазина Монеток`;
    const html = `<h2>Восстановление пароля</h2><p>Здравствуйте!</p><p>Вы запросили восстановление пароля для вашего магазина "<b>${data.name}</b>".</p><p>Пароль администратора: <b>${data.admin_password}</b></p><br><p>С уважением,<br>Команда Магазина Монеток</p>`;

    return await sendEmail({ to: email, subject, text, html });
}

module.exports = {
    loadFamilies,
    saveFamilies,
    findFamilyByEmail,
    findFamilyById,
    authenticateUser,
    authenticateChildByToken,
    loadFamilyData,
    saveFamilyData,
    registerFamily,
    changePassword,
    loadBaseData,
    saveBaseData,
    toggleFamilyBlock,
    updateLastActivity,
    recoverPassword,
    getChildLoginLink,
    regenerateChildToken,
    updateFamilyName,
    updateFamilySettings
};
