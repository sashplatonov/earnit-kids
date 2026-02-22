const test = require('node:test');
const assert = require('node:assert');
const proxyquire = require('proxyquire');

// Mock data
const mockFamilyDB = {
    testfamily: {
        id: 'testfamily',
        name: 'Test Family',
        email: 'test@example.com',
        admin_password: 'Password123!',
        isBlocked: false,
        isVerified: true
    },
    superAdminFamily: {
        id: 'superadmin',
        email: 'admin@system.local',
        isSuperAdmin: true,
        password: 'AdminSuperPassword'
    }
};

const authService = proxyquire('../../src/services/authService', {
    '../db/familyRepository': {
        findAll: async () => {
            return {
                super_admin: mockFamilyDB.superAdminFamily
            };
        },
        findByEmail: async (email) => {
            const family = Object.values(mockFamilyDB).find(f => f.email === email);
            return family || null;
        },
        findById: async (id) => mockFamilyDB[id] || null,
        create: async (data) => ({ success: true, id: data.family_id }),
        update: async (id, data) => true
    }
});

test('isValidPassword', () => {
    assert.strictEqual(authService.isValidPassword('12345'), false, 'Should be false for short password');
    assert.strictEqual(authService.isValidPassword('111111'), false, 'Should be false if all chars are the same');
    assert.strictEqual(authService.isValidPassword('Hello123!'), true, 'Should be true for valid password');
});

test('authenticateUser: Success as admin', async () => {
    const result = await authService.authenticateUser('test@example.com', 'Password123!');
    assert.strictEqual(result.success, true);
    assert.strictEqual(result.role, 'admin');
    assert.strictEqual(result.familyId, 'testfamily');
});

test('authenticateUser: Incorrect password', async () => {
    const result = await authService.authenticateUser('test@example.com', 'wrongpassword');
    assert.strictEqual(result.success, false);
    assert.strictEqual(result.error, 'Неверные учетные данные');
});

test('authenticateUser: Success as super admin', async () => {
    // Note: SUPER_ADMIN_PASSWORD might override the password in the mock, but if undefined it falls back
    const expectedPassword = process.env.SUPER_ADMIN_PASSWORD || 'AdminSuperPassword';
    const result = await authService.authenticateUser('admin@system.local', expectedPassword);
    assert.strictEqual(result.success, true);
    assert.strictEqual(result.role, 'super_admin');
});
