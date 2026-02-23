const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const { describe, it } = require('node:test');

describe('data directory placeholder', () => {
    it('keeps a tracked .gitkeep so Docker COPY works', () => {
        const dataDir = path.join(__dirname, '..', '..', 'data');
        const gitkeep = path.join(dataDir, '.gitkeep');
        assert.ok(fs.existsSync(dataDir), 'data directory should exist');
        assert.ok(fs.existsSync(gitkeep), '.gitkeep should be tracked for data/');
    });
});
