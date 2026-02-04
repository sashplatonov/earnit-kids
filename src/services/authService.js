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

        // Check verification (only if enabled)
        const emailVerificationEnabled = process.env.ENABLE_EMAIL_VERIFICATION !== 'false';
        if (emailVerificationEnabled && family.isVerified === false) {
            console.log('  - Login FAILED: Email not verified');
            return { success: false, error: 'Email не подтвержден. Проверьте почту.' };
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

    // Feature Flag: Email Verification
    const emailVerificationEnabled = process.env.ENABLE_EMAIL_VERIFICATION !== 'false';
    const isVerified = !emailVerificationEnabled;

    // Generate verification token only if needed
    const verificationToken = emailVerificationEnabled ? crypto.randomBytes(32).toString('hex') : null;

    try {
        const result = await familyRepository.create({
            family_id: familyId,
            name: familyName || `Шоп ${familyId}`,
            email: email,
            admin_password: adminPassword,
            child_token: crypto.randomBytes(32).toString('hex'),
            monthly_limit: 10000,
            child_nickname: '',
            isVerified: isVerified,
            verification_token: verificationToken
        });

        if (result.success) {
            // Send verification email only if enabled
            if (emailVerificationEnabled) {
                const { sendVerificationEmail } = require('./emailService');
                // Use token based verification link
                const verificationLink = `${process.env.APP_URL || 'http://localhost:3000'}/verify?token=${verificationToken}&email=${encodeURIComponent(email)}`;

                sendVerificationEmail(email, verificationLink).catch(err => console.error('Failed to send verification email:', err));
            }

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
    recoverPassword,
    resetPasswordWithToken,
    verifyEmailToken
};
