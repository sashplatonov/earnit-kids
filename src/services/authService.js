/** @file Auth Service business services */
/**
 * Auth Service - Authentication business logic
 * Uses PostgreSQL database via repositories
 */

const crypto = require('crypto');
const familyRepository = require('../db/familyRepository');
const familyDataRepository = require('../db/familyDataRepository');
const { createLogger } = require('../utils/logger');
const logger = createLogger('authService');

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
 * Authenticate super admin
 */
async function authenticateSuperAdmin(email, password) {
    const superAdminEmail = process.env.SUPER_ADMIN_EMAIL;
    if (superAdminEmail !== email) return null;

    const expectedPassword = process.env.SUPER_ADMIN_PASSWORD;
    if (expectedPassword === password) {
        return { success: true, role: 'super_admin', familyId: null };
    }
    return { success: false, error: 'Неверный пароль администратора' };
}

/**
 * Authenticate family admin
 */
async function authenticateFamily(email, password) {
    const family = await familyRepository.findByEmail(email);
    if (!family || family.isSuperAdmin) return null;

    if (family.isBlocked) {
        return { success: false, error: 'Аккаунт заблокирован' };
    }

    const emailVarEnabled = process.env.ENABLE_EMAIL_VERIFICATION !== 'false';
    if (emailVarEnabled && family.isVerified === false) {
        return { success: false, error: 'Email не подтвержден. Проверьте почту.' };
    }

    if (family.admin_password === password) {
        return { success: true, role: 'admin', familyId: family.id };
    }
    return { success: false, error: 'Неверный пароль' };
}

/**
 * Authenticate user (parent or super admin)
 * @param {string} email 
 * @param {string} password 
 * @returns {Promise<Object>}
 */
async function authenticateUser(email, password) {
    logger.debug({ email }, 'Authentication attempt');

    const superRes = await authenticateSuperAdmin(email, password);
    if (superRes) {
        if (superRes.success) {
            // intentional silence on success
        } else {
            logger.warn({ email }, 'Super admin login failed');
        }
        return superRes;
    }

    const familyRes = await authenticateFamily(email, password);
    if (familyRes) {
        if (familyRes.success) {
            // success state already tracked
        } else {
            logger.warn({ email, error: familyRes.error }, 'Family login failed');
        }
        return familyRes;
    }

    logger.warn({ email }, 'Authentication failed: unknown user');
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

    const data = await familyRepository.findByChildToken(token);
    if (data) {
        if (data.isBlocked) {
            return { success: false, error: 'Аккаунт заблокирован' };
        }
        return {
            success: true,
            role: 'child',
            familyId: data.id,
            email: data.email,
            childId: data.currentChild ? data.currentChild.id : null,
            childName: data.currentChild ? data.currentChild.name : 'Unknown'
        };
    }

    return { success: false, error: 'Неверная ссылка' };
}

async function prepareVerification(email) {
    const enabled = process.env.ENABLE_EMAIL_VERIFICATION !== 'false';
    const token = enabled ? crypto.randomBytes(32).toString('hex') : null;
    return { enabled, token };
}

function buildFamilyPayload({ email, adminPassword, token, enabled }) {
    return {
        family_id: `${email.replace(/[^a-zA-Z0-9]/g, '_')}_${Date.now()}`,
        email, admin_password: adminPassword,
        child_token: crypto.randomBytes(32).toString('hex'),
        monthly_limit: 10000,
        child_nickname: '',
        isVerified: !enabled,
        verification_token: token
    };
}

async function handleVerificationEmail(email, token) {
    const { sendVerificationEmail } = require('./emailService');
    const baseUrl = process.env.APP_URL || 'http://localhost:3001';
    const link = `${baseUrl}/verify?token=${token}&email=${encodeURIComponent(email)}`;
    await sendVerificationEmail(email, link).catch(err => logger.error({ err: err.message }, 'Verification email failed'));
}

async function registerFamily(email, adminPassword) {
    const existing = await familyRepository.findByEmail(email);
    if (existing && !existing.isSuperAdmin) return { success: false, error: 'Email уже зарегистрирован' };
    if (!isValidPassword(adminPassword)) return { success: false, error: 'Слабый пароль родителя' };

    const { enabled, token } = await prepareVerification(email);
    try {
        const payload = buildFamilyPayload({ email, adminPassword, token, enabled });
        const result = await familyRepository.create(payload);

        if (result.success && enabled) {
            await handleVerificationEmail(email, token);
        }

        return result.success ? { success: true, familyId: result.familyId } : { success: false, error: 'Ошибка сохранения' };
    } catch (err) {
        const isDupe = err.code === '23505';
        return { success: false, error: isDupe ? 'Email уже зарегистрирован' : 'Ошибка сохранения' };
    }
}

/**
 * Change password
 */
async function changePassword({ familyId, role, oldPassword, newPassword }) {
    if (role !== 'admin') return { success: false, error: 'Forbidden' };

    const family = await familyRepository.findById(familyId);
    if (!family) return { success: false, error: 'Family not found' };

    if (family.admin_password !== oldPassword) return { success: false, error: 'Incorrect old password' };
    if (!isValidPassword(newPassword)) return { success: false, error: 'Weak password' };

    const success = await familyRepository.update(familyId, { admin_password: newPassword });
    return success ? { success: true } : { success: false, error: 'Save failed' };
}

/**
 * Recover password via email
 * @param {string} email 
 * @returns {Promise<Object>}
 */
async function recoverPassword(email) {
    // Feature Flag: Password Recovery
    if (process.env.ENABLE_PASSWORD_RECOVERY === 'false') {
        return { success: false, error: 'Функция восстановления пароля отключена' };
    }

    const { sendEmail, sendVerificationEmail, sendPasswordResetEmail } = require('./emailService');

    // Check Super Admin
    const superAdminEmail = process.env.SUPER_ADMIN_EMAIL;
    const superAdminPassword = process.env.SUPER_ADMIN_PASSWORD;

    if (superAdminEmail && superAdminEmail === email && superAdminPassword) {
        const loginLink = `${process.env.APP_URL || 'http://localhost:3000'}/login`;
        // Use the specialized function which pulls subject/text/html from external files
        const { sendSuperAdminRecoveryEmail } = require('./emailService');
        return await sendSuperAdminRecoveryEmail(email, superAdminPassword, loginLink);
    }

    const family = await familyRepository.findByEmail(email);
    if (!family || family.isSuperAdmin) {
        return { success: false, error: 'User not found' };
    }

    // TODO: Generate a real secure token and store it in the database with expiration
    const resetToken = crypto.randomBytes(32).toString('hex');
    // For now we simulate a link. The backend route handling /reset-password needs to be implemented.
    const resetLink = `${process.env.APP_URL || 'http://localhost:3000'}/reset-password?token=${resetToken}&email=${encodeURIComponent(email)}`;

    return await sendPasswordResetEmail(email, resetLink);
}

/**
 * Reset password with token
 * @param {string} email 
 * @param {string} token 
 * @param {string} newPassword 
 * @returns {Promise<Object>}
 */
async function resetPasswordWithToken(email, token, newPassword) {
    // TODO: Verify token against DB
    // For now, we trust the email + token presence because we haven't implemented token storage yet.
    // Ideally: const storedToken = await familyRepository.getResetToken(email);
    // if (!storedToken || storedToken !== token) return { success: false, error: 'Invalid token' };

    if (!token) return { success: false, error: 'Token missing' };
    if (!isValidPassword(newPassword)) return { success: false, error: 'Weak password' };

    const family = await familyRepository.findByEmail(email);
    if (!family) return { success: false, error: 'User not found' };

    if (await familyRepository.update(family.id, { admin_password: newPassword })) {
        return { success: true };
    }
    return { success: false, error: 'Save failed' };
}

/**
 * Verify email with token
 * @param {string} email 
 * @param {string} token 
 * @returns {Promise<Object>}
 */
async function verifyEmailToken(email, token) {
    if (!token) return { success: false, error: 'Token missing' };

    const family = await familyRepository.findByEmail(email);
    if (!family) return { success: false, error: 'User not found' };

    // Find by verification token to ensure it matches
    const tokenFamily = await familyRepository.findByVerificationToken(token);

    // Check if token belongs to the user
    if (!tokenFamily || tokenFamily.id !== family.id) {
        return { success: false, error: 'Invalid or expired token' };
    }

    if (await familyRepository.verifyFamily(family.id)) {
        return { success: true };
    }
    return { success: false, error: 'Verification failed' };
}

module.exports = {
    authenticateUser,
    authenticateChildByToken,
    registerFamily,
    isValidPassword,
    changePassword,
    recoverPassword,
    resetPasswordWithToken,
    verifyEmailToken
};
