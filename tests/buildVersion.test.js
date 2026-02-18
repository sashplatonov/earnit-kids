const test = require('node:test');
const assert = require('node:assert/strict');

const { formatBuildValue, getBuildVersion } = require('../src/utils/buildVersion');

const ENV_KEYS = [
    'BUILD_TIMESTAMP',
    'RENDER_BUILD_TIMESTAMP',
    'RENDER_DEPLOYMENT_START_TIME',
];

function clearBuildEnv() {
    for (const key of ENV_KEYS) {
        delete process.env[key];
    }
}

test('formatBuildValue formats unix timestamp as UTC', () => {
    const value = formatBuildValue(1704067200000);
    assert.equal(value, '2024-01-01-00-00');
});

test('formatBuildValue keeps valid hyphenated timestamp', () => {
    const value = formatBuildValue('2025-02-03-04-05');
    assert.equal(value, '2025-02-03-04-05');
});

test('getBuildVersion uses first configured environment key', () => {
    clearBuildEnv();
    process.env.RENDER_BUILD_TIMESTAMP = '2025-07-10-09-08';
    process.env.RENDER_DEPLOYMENT_START_TIME = '2025-01-01-00-00';

    const value = getBuildVersion();
    assert.equal(value, '2025-07-10-09-08');
    clearBuildEnv();
});

test('getBuildVersion prefers BUILD_TIMESTAMP over other keys', () => {
    clearBuildEnv();
    process.env.BUILD_TIMESTAMP = '2023-12-31-23-59';
    process.env.RENDER_BUILD_TIMESTAMP = '2024-01-01-00-00';

    const value = getBuildVersion();
    assert.equal(value, '2023-12-31-23-59');
    clearBuildEnv();
});
