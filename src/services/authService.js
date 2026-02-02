const crypto = require('crypto');
const { loadFamilies, saveFamilies, loadFamilyData, saveFamilyData } = require('./familyService');
const { DEFAULT_FAMILY_DATA } = require('./familyService');

function isValidPassword(password) {
    if (!password || password.length < 6) return false;
    const firstChar = password[0];
    const allSame = password.split('').every(char => char === firstChar);
    if (allSame) return false;
    return true;
}

function authenticateUser(email, password) {
    const familiesData = loadFamilies();

    // Super Admin
    if (familiesData.super_admin && familiesData.super_admin.email === email) {
        if (familiesData.super_admin.password === password) {
            return { success: true, role: 'super_admin', familyName: 'Super Admin', familyId: null };
        }
    }

    // Family
    const entry = Object.entries(familiesData.families).find(([id, data]) => data.email === email);
    if (entry) {
        const [familyId, data] = entry;
        if (data.isBlocked) return { success: false, error: 'Аккаунт заблокирован' };

        if (data.admin_password === password) {
            return { success: true, role: 'admin', familyName: data.name, familyId: familyId };
        }
        if (data.child_password === password) {
            return { success: true, role: 'child', familyName: data.name, familyId: familyId };
        }
    }
    return { success: false, error: 'Неверные учетные данные' };
}

function authenticateChildByToken(token) {
    if (!token) return { success: false, error: 'Токен отсутствует' };
    const familiesData = loadFamilies();
    const entry = Object.entries(familiesData.families).find(([id, data]) => data.child_token === token);

    if (entry) {
        const [familyId, data] = entry;
        if (data.isBlocked) return { success: false, error: 'Аккаунт заблокирован' };
        return { success: true, role: 'child', familyName: data.name, familyId: familyId, email: data.email };
    }
    return { success: false, error: 'Неверная ссылка' };
}

function registerFamily(familyName, email, adminPassword, childPassword) {
    const familiesData = loadFamilies();
    if (Object.values(familiesData.families).some(f => f.email === email)) {
        return { success: false, error: 'Email уже зарегистрирован' };
    }

    if (!isValidPassword(adminPassword)) {
        return { success: false, error: 'Слабый пароль родителя' };
    }

    if (!childPassword) {
        childPassword = crypto.randomBytes(8).toString('hex');
    }

    const familyId = `${email.replace(/[^a-zA-Z0-9]/g, '_')}_${Date.now()}`;
    familiesData.families[familyId] = {
        name: familyName || `Шоп ${familyId}`,
        email: email,
        created_at: new Date().toISOString(),
        admin_password: adminPassword,
        child_password: childPassword,
        child_token: crypto.randomBytes(32).toString('hex'),
        monthly_limit: 10000
    };

    if (saveFamilies(familiesData)) {
        saveFamilyData(familyId, { ...DEFAULT_FAMILY_DATA });
        return { success: true, familyId };
    }
    return { success: false, error: 'Ошибка сохранения' };
}

function changePassword(familyId, role, oldPassword, newPassword) {
    const familiesData = loadFamilies();
    const family = familiesData.families[familyId];
    if (!family) return { success: false, error: 'Family not found' };

    const currentPass = role === 'admin' ? family.admin_password : family.child_password;
    if (currentPass !== oldPassword) return { success: false, error: 'Incorrect old password' };

    if (!isValidPassword(newPassword)) return { success: false, error: 'Weak password' };

    if (role === 'admin') family.admin_password = newPassword;
    else family.child_password = newPassword;

    if (saveFamilies(familiesData)) return { success: true };
    return { success: false, error: 'Save failed' };
}

async function recoverPassword(email) {
    const familiesData = loadFamilies();
    const { sendEmail } = require('./emailService');

    const entry = Object.entries(familiesData.families).find(([id, data]) => data.email === email);

    // Check Super Admin
    if (familiesData.super_admin && familiesData.super_admin.email === email) {
        return await sendEmail({
            to: email,
            subject: 'Восстановление пароля - Super Admin',
            text: `Ваш пароль: ${familiesData.super_admin.password}`,
            html: `<h2>Восстановление пароля</h2><p>Ваш пароль: <b>${familiesData.super_admin.password}</b></p>`
        });
    }

    if (!entry) return { success: false, error: 'User not found' };

    const [id, data] = entry;
    return await sendEmail({
        to: email,
        subject: 'Восстановление пароля - Монетки',
        text: `Ваш пароль администратора: ${data.admin_password}`,
        html: `<h2>Восстановление пароля</h2><p>Пароль администратора: <b>${data.admin_password}</b></p>`
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
