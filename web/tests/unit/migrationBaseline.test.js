const test = require('node:test');
const assert = require('node:assert/strict');
const proxyquire = require('proxyquire').noCallThru();

const { isExistingSchemaBaselineReady } = proxyquire('../../scripts/migrate', {
    dotenv: { config() {} },
    '../src/db/connection': {
        pool: {
            query() {},
            connect() {}
        },
        query() {}
    },
    '../src/db/schema': {
        getDatabaseSchema: () => 'earnit_kids',
        quoteIdentifier: (value) => `"${value}"`
    }
});

function buildSignals({ tables = [], columns = [], functions = [] } = {}) {
    return {
        tables: new Set(tables),
        columns: new Set(columns),
        functions: new Set(functions)
    };
}

test('baseline detection recognizes an already-bootstrapped project schema', () => {
    const signals = buildSignals({
        tables: ['families', 'children', 'tasks', 'shop_items', 'requests', 'device_push_tokens'],
        columns: [
            'tasks.child_id',
            'shop_items.child_id',
            'requests.request_type',
            'requests.item_id',
            'requests.money_amount',
            'children.daily_coin_limit',
            'children.theme',
            'families.last_selected_child_id',
            'families.reset_token',
            'families.updated_at',
            'device_push_tokens.endpoint',
            'device_push_tokens.push_type'
        ],
        functions: ['update_updated_at_column']
    });

    assert.equal(isExistingSchemaBaselineReady(signals), true);
});

test('baseline detection rejects schemas that still look pre-multi-child or mid-migration', () => {
    const signals = buildSignals({
        tables: ['families', 'children', 'tasks', 'shop_items', 'requests', 'device_push_tokens', 'family_data'],
        columns: [
            'families.child_token',
            'tasks.child_id',
            'shop_items.child_id',
            'requests.request_type',
            'children.daily_coin_limit',
            'families.updated_at'
        ],
        functions: ['update_updated_at_column']
    });

    assert.equal(isExistingSchemaBaselineReady(signals), false);
});