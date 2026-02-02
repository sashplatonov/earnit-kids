const fs = require('fs');
const path = require('path');

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
    if (!isValidPassword(childPassword)) {
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
        child_password: childPassword
    };

    if (saveFamilies(families)) {
        // Create default family data
        let familyData = { ...DEFAULT_FAMILY_DATA };
        saveFamilyData(familyId, familyData);
        return { success: true, familyId };
    }

    return { success: false, error: 'Ошибка сохранения' };
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

module.exports = {
    loadFamilies,
    saveFamilies,
    findFamilyByEmail,
    findFamilyById,
    authenticateUser,
    loadFamilyData,
    saveFamilyData,
    registerFamily,
    changePassword,
    loadBaseData,
    saveBaseData,
    toggleFamilyBlock,
    updateLastActivity
};
