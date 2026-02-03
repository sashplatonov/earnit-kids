/**
 * Auth Service - Authentication business logic
 * Uses PostgreSQL database via repositories
 */

const crypto = require('crypto');
const familyRepository = require('../db/familyRepository');
const familyDataRepository = require('../db/familyDataRepository');

/**
 * Validate password strength
 * @param {string} password 
 * @returns {boolean}
 */
function isValidPassword(password) {
    if (!password || password.length < 6) return false;
    const firstChar = password[0];
    const allSame = password.split('').every(char => char === firstChar);
    if (allSame) return false;
    return true;
}

/**
 * Authenticate user (parent or super admin)
 * @param {string} email 
 * @param {string} password 
 * @returns {Promise<Object>}
 */
async function authenticateUser(email, password) {
    console.log(`🔐 Authentication attempt for email: ${email}`);
    const familiesData = await familyRepository.findAll();

    // Super Admin check (from env or database)
    const superAdmin = familiesData.super_admin;
    if (superAdmin && superAdmin.email === email) {
        console.log('  - Email matches super admin');
        const expectedPassword = process.env.SUPER_ADMIN_PASSWORD || superAdmin.password;
        if (expectedPassword === password) {
            console.log('  - Super admin login SUCCESS');
            return { success: true, role: 'super_admin', familyName: 'Super Admin', familyId: null };
        } else {
            console.log('  - Super admin login FAILED: Incorrect password');
        }
    }

    // Family check
    const family = await familyRepository.findByEmail(email);
    if (family && !family.isSuperAdmin) {
        console.log(`  - Found family: ${family.name}`);
        if (family.isBlocked) {
            console.log('  - Login FAILED: Account is blocked');
            return { success: false, error: 'Аккаунт заблокирован' };
        }

        if (family.admin_password === password) {
            console.log('  - Family admin login SUCCESS');
            return {
                success: true,
                role: 'admin',
                familyName: family.name,
                familyId: family.id
            };
        } else {
            console.log('  - Family admin login FAILED: Incorrect password');
        }
    }

    console.log('  - Authentication FAILED: User not found or incorrect credentials');
    return { success: false, error: 'Неверные учетные данные' };
}

/**
 * Authenticate child by token (magic link)
 * @param {string} token 
 * @returns {Promise<Object>}
 */
async function authenticateChildByToken(token) {
    if (!token) {
        return { success: false, error: 'Токен отсутствует' };
    }

    const family = await familyRepository.findByChildToken(token);
    if (family) {
        if (family.isBlocked) {
            return { success: false, error: 'Аккаунт заблокирован' };
        }
        return {
            success: true,
            role: 'child',
            familyName: family.name,
            familyId: family.id,
            email: family.email
        };
    }

    return { success: false, error: 'Неверная ссылка' };
}

/**
 * Register a new family
 * @param {string} familyName 
 * @param {string} email 
 * @param {string} adminPassword 
 * @returns {Promise<Object>}
 */
async function registerFamily(familyName, email, adminPassword) {
    // Check if email exists
    const existing = await familyRepository.findByEmail(email);
    if (existing && !existing.isSuperAdmin) {
        return { success: false, error: 'Email уже зарегистрирован' };
    }

    if (!isValidPassword(adminPassword)) {
        return { success: false, error: 'Слабый пароль родителя' };
    }

    const familyId = `${email.replace(/[^a-zA-Z0-9]/g, '_')}_${Date.now()}`;

    try {
        const result = await familyRepository.create({
            family_id: familyId,
            name: familyName || `Шоп ${familyId}`,
            email: email,
            admin_password: adminPassword,
            child_token: crypto.randomBytes(32).toString('hex'),
            monthly_limit: 10000,
            child_nickname: ''
        });

        if (result.success) {
            return { success: true, familyId };
        }
        return { success: false, error: 'Ошибка сохранения' };
    } catch (err) {
        console.error('Registration error:', err.message);
        if (err.code === '23505') { // unique violation
            return { success: false, error: 'Email уже зарегистрирован' };
        }
        return { success: false, error: 'Ошибка сохранения' };
    }
}

/**
 * Change password
 * @param {string} familyId 
 * @param {string} role 
 * @param {string} oldPassword 
 * @param {string} newPassword 
 * @returns {Promise<Object>}
 */
async function changePassword(familyId, role, oldPassword, newPassword) {
    const family = await familyRepository.findById(familyId);
    if (!family) {
        return { success: false, error: 'Family not found' };
    }

    if (role !== 'admin') {
        return { success: false, error: 'Forbidden' };
    }

    if (family.admin_password !== oldPassword) {
        return { success: false, error: 'Incorrect old password' };
    }

    if (!isValidPassword(newPassword)) {
        return { success: false, error: 'Weak password' };
    }

    if (await familyRepository.update(familyId, { admin_password: newPassword })) {
        return { success: true };
    }
    return { success: false, error: 'Save failed' };
}

/**
 * Recover password via email
 * @param {string} email 
 * @returns {Promise<Object>}
 */
async function recoverPassword(email) {
    const { sendEmail } = require('./emailService');

    // Check Super Admin
    const superAdminEmail = process.env.SUPER_ADMIN_EMAIL;
    const superAdminPassword = process.env.SUPER_ADMIN_PASSWORD;

    if (superAdminEmail && superAdminEmail === email && superAdminPassword) {
        return await sendEmail({
            to: email,
            subject: 'Восстановление пароля - Super Admin',
            text: `Ваш пароль: ${superAdminPassword}`,
            html: `<h2>Восстановление пароля</h2><p>Ваш пароль: <b>${superAdminPassword}</b></p>`
        });
    }

    const family = await familyRepository.findByEmail(email);
    if (!family || family.isSuperAdmin) {
        return { success: false, error: 'User not found' };
    }

    return await sendEmail({
        to: email,
        subject: 'Восстановление пароля - Монетки',
        text: `Ваш пароль администратора: ${family.admin_password}`,
        html: `<h2>Восстановление пароля</h2><p>Пароль администратора: <b>${family.admin_password}</b></p>`
    });
}

module.exports = {
    authenticateUser,
    authenticateChildByToken,
    registerFamily,
    isValidPassword,
    changePassword,
    recoverPassword
};
