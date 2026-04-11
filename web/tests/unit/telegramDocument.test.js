const assert = require('node:assert');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { describe, it, beforeEach, afterEach } = require('node:test');

process.env.ENABLE_TELEGRAM_ALERTS = 'true';
process.env.TELEGRAM_BOT_TOKEN = 'fake-token';
process.env.TELEGRAM_CHAT_ID = '12345';

const { sendTelegramDocument } = require('../../src/utils/alerts');
let capturedFormData;
let originalFetch;
let originalFormData;
let originalBlob;

function setupTelegramMocks() {
    originalFetch = global.fetch;
    originalFormData = global.FormData;
    originalBlob = global.Blob;
    capturedFormData = null;

    class MockFormData {
        constructor() {
            this.entries = [];
            capturedFormData = this;
        }

        append(name, value, filename) {
            this.entries.push({ name, value, filename });
        }
    }

    global.FormData = MockFormData;
    global.Blob = class {
        constructor(parts) {
            this.parts = parts;
        }
    };

    global.fetch = async () => ({ ok: true });
}

function teardownTelegramMocks() {
    global.fetch = originalFetch;
    global.FormData = originalFormData;
    global.Blob = originalBlob;
}

describe('sendTelegramDocument', function () {
    beforeEach(setupTelegramMocks);
    afterEach(teardownTelegramMocks);

    it('attaches parse_mode=HTML by default for captions', async () => {
        const tempFile = path.join(os.tmpdir(), 'telegram-doc-test.txt');
        fs.writeFileSync(tempFile, 'payload');

        const result = await sendTelegramDocument(tempFile, 'caption text');

        fs.unlinkSync(tempFile);
        assert.strictEqual(result, true);
        const appendArgs = capturedFormData.entries;
        const parseField = appendArgs.find(entry => entry.name === 'parse_mode');

        assert.ok(parseField, 'parse_mode should be appended');
        assert.strictEqual(parseField.value, 'HTML');
    });

    it('respects parseMode override when provided', async () => {
        const tempFile = path.join(os.tmpdir(), 'telegram-doc-test.txt');
        fs.writeFileSync(tempFile, 'payload');

        const result = await sendTelegramDocument(tempFile, 'caption text', { parseMode: 'MarkdownV2' });

        fs.unlinkSync(tempFile);
        assert.strictEqual(result, true);
        const appendArgs = capturedFormData.entries;
        const parseField = appendArgs.find(entry => entry.name === 'parse_mode');

        assert.ok(parseField, 'parse_mode should be appended when override provided');
        assert.strictEqual(parseField.value, 'MarkdownV2');
    });
});
