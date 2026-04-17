const test = require('node:test');
const assert = require('node:assert/strict');

const { DEFAULT_CHILD_KEY, buildLegacyFamilySnapshot } = require('./legacyFamilyData');

test('multi-child payload keeps tasks and shop bound to the correct child', () => {
    const snapshot = buildLegacyFamilySnapshot(
        'fam-e2e',
        { email: 'parent@example.com', admin_password: 'secret' },
        {
            balance: 180,
            tasks: [
                { id: 501, name: 'Собрать рюкзак', coins: 20, childId: 101 },
                { id: 502, name: 'Полить цветы', coins: 10, childId: 102 }
            ],
            shop: [
                { id: 1001, name: 'Поход в кино', price: 120, childId: 101, comment: 'С папой' },
                { id: 1002, name: 'Мороженое', price: 40, childId: 102, comment: 'Клубничное' }
            ],
            children: [
                { id: 101, name: 'Аня', balance: 180, monthlyLimit: 900, dailyCoinLimit: 40 },
                { id: 102, name: 'Илья', balance: 90, monthlyLimit: 500, dailyCoinLimit: 25 }
            ]
        }
    );

    assert.equal(snapshot.children.length, 2);
    assert.deepEqual(snapshot.tasks.map((task) => task.childKey), ['legacy:101', 'legacy:102']);
    assert.deepEqual(snapshot.shopItems.map((item) => item.childKey), ['legacy:101', 'legacy:102']);
    assert.deepEqual(snapshot.shopItems.map((item) => item.comment), ['С папой', 'Клубничное']);
    assert.equal(snapshot.preferredChildKey, 'legacy:101');
});

test('single-child payload falls back to the default child for legacy entries without childId', () => {
    const snapshot = buildLegacyFamilySnapshot(
        'fam-single',
        {
            email: 'parent@example.com',
            admin_password: 'secret',
            child_nickname: 'Кира',
            child_token: 'legacy-token',
            monthly_limit: 700
        },
        {
            balance: 160,
            monthlyLimit: 1000,
            dailyCoinLimit: 50,
            tasks: [
                { id: 1, name: 'Убрать игрушки', coins: 15 }
            ],
            shop: [
                { id: 2, name: 'Поход в парк', price: 80 }
            ],
            history: [
                { id: 3, type: 'earn', amount: 15 }
            ],
            requests: [
                { id: 4, taskId: 1, taskName: 'Убрать игрушки', coins: 15 }
            ]
        }
    );

    assert.equal(snapshot.children.length, 1);
    assert.equal(snapshot.children[0].legacyKey, DEFAULT_CHILD_KEY);
    assert.equal(snapshot.children[0].name, 'Кира');
    assert.equal(snapshot.children[0].token, 'legacy-token');
    assert.equal(snapshot.children[0].balance, 160);
    assert.equal(snapshot.children[0].monthlyLimit, 1000);
    assert.equal(snapshot.children[0].dailyCoinLimit, 50);
    assert.equal(snapshot.tasks[0].childKey, DEFAULT_CHILD_KEY);
    assert.equal(snapshot.shopItems[0].childKey, DEFAULT_CHILD_KEY);
    assert.equal(snapshot.historyEntries[0].childKey, DEFAULT_CHILD_KEY);
    assert.equal(snapshot.requests[0].childKey, DEFAULT_CHILD_KEY);
    assert.equal(snapshot.hasScopedData, true);
});

test('missing family JSON does not mark scoped collections as present', () => {
    const snapshot = buildLegacyFamilySnapshot(
        'fam-empty',
        {
            email: 'parent@example.com',
            admin_password: 'secret',
            child_nickname: 'Лева'
        },
        {}
    );

    assert.equal(snapshot.hasScopedData, false);
    assert.equal(snapshot.tasks.length, 0);
    assert.equal(snapshot.shopItems.length, 0);
});
