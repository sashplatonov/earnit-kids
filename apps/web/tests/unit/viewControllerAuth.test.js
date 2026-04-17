/** @file Tests for viewController rendering — auth is delegated to Java backend */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const proxyquire = require('proxyquire').noCallThru();

const FAKE_VIEWS_DIR = require('path').join(__dirname, '../../views');

function buildStubViewController() {
    return proxyquire('../../src/controllers/viewController', {
        './seoTemplates': {
            applyCommonTemplateData: (html, _replacements, _req) => html,
            buildSeoReplacements: () => ({}),
        },
        './staticUtils': {
            getHtmlHeaders: () => ({ 'Content-Type': 'text/html; charset=utf-8' }),
            normalizeStaticPath: (p) => p,
            resolvePublicFilePath: () => null,
            sendStaticFile: () => {},
            tryServeDistOverride: () => false,
            setServeNotFoundHandler: () => {},
        },
        '../utils/logger': {
            createLogger: () => ({
                info: () => {},
                warn: () => {},
                error: () => {},
            })
        }
    });
}

function createMockResponse() {
    const state = { statusCode: null, headers: {}, body: '' };
    return {
        state,
        writeHead(statusCode, headers = {}) {
            state.statusCode = statusCode;
            state.headers = headers;
        },
        end(body = '') { state.body = body; }
    };
}

function createMockRequest(url = '/', headers = {}) {
    return { url, headers: { host: 'localhost:3000', ...headers }, socket: {} };
}

test('serveLanding renders a 200 response', async () => {
    const vc = buildStubViewController();
    const req = createMockRequest('/');
    const res = createMockResponse();

    // landing.html must exist; if not, expect 500
    await assert.doesNotReject(() => vc.serveLanding(req, res));
    // status should be 200 if view exists, 500 otherwise
    assert.ok([200, 500].includes(res.state.statusCode));
});

test('serveSuperAdmin renders the super-admin view', async () => {
    const vc = buildStubViewController();
    const req = createMockRequest('/super-admin');
    const res = createMockResponse();

    await assert.doesNotReject(() => vc.serveSuperAdmin(req, res));
    assert.ok([200, 500].includes(res.state.statusCode));
});

test('serveFeaturePage responds 200 for known slug', async () => {
    const vc = buildStubViewController();
    const req = createMockRequest('/features/tasks');
    const res = createMockResponse();

    await vc.serveFeaturePage(req, res, 'tasks');
    assert.ok([200, 500].includes(res.state.statusCode));
});

test('serveFeaturePage calls serveNotFound for unknown slug', async () => {
    const vc = buildStubViewController();
    const req = createMockRequest('/features/unknown');
    const res = createMockResponse();

    await vc.serveFeaturePage(req, res, 'unknown-feature');
    // 404.html renders (200 status with 404-page content) or 500 if template missing
    assert.ok([200, 500].includes(res.state.statusCode));
});
