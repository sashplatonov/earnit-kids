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

// Set test environment variables
process.env.SUPER_ADMIN_EMAIL = 'admin@system.local';
process.env.SUPER_ADMIN_PASSWORD = 'AdminSuperPassword';

function createAuthServiceWithRepo(overrides = {}) {
    const baseRepo = {
        findAll: async () => ({ super_admin: mockFamilyDB.superAdminFamily }),
        findByEmail: async (email) => {
            const family = Object.values(mockFamilyDB).find(f => f.email === email);
            return family || null;
        },
        findById: async (id) => mockFamilyDB[id] || null,
        create: async (data) => ({ success: true, familyId: data.family_id }),
        update: async () => true
    };
    delete require.cache[require.resolve('../../src/services/authService')];
    return proxyquire('../../src/services/authService', {
        '../db/familyRepository': { ...baseRepo, ...overrides }
    });
}

const authService = createAuthServiceWithRepo();

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
    assert.strictEqual(result.error, 'Неверный пароль');
});

test('authenticateUser: Success as super admin', async () => {
    const result = await authService.authenticateUser('admin@system.local', 'AdminSuperPassword');
    assert.strictEqual(result.success, true);
    assert.strictEqual(result.role, 'super_admin');
});

test('registerFamily does not rely on store name input', async () => {
    let capturedPayload;
    const service = createAuthServiceWithRepo({
        create: async (data) => {
            capturedPayload = data;
            return { success: true, familyId: data.family_id };
        }
    });
    const response = await service.registerFamily('register@example.com', 'Password456!');
    assert.strictEqual(response.success, true);
    assert.strictEqual(capturedPayload.name, undefined);
    assert.ok(capturedPayload.family_id);
});
