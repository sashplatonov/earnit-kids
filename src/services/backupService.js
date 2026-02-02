const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');
const { DATA_DIR } = require('../config');

/**
 * Creates a zip backup of the data folder and streams it to the response
 * @param {import('http').IncomingMessage} req
 * @param {import('http').ServerResponse} res 
 */
function createBackup(req, res) {
    if (!fs.existsSync(DATA_DIR)) {
        res.writeHead(404, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({ error: 'Data folder not found' }));
    }

    const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
    const filename = `backup-data-${timestamp}.zip`;

    // Use zip command to stream to stdout
    // -r: recursive
    // -: stream to stdout
    // data: the folder to zip
    // We run the command from the parent directory of DATA_DIR to preserve 'data/' prefix in zip
    const parentDir = path.dirname(DATA_DIR);
    const dataFolderName = path.basename(DATA_DIR);

    const zip = spawn('zip', ['-r', '-', dataFolderName], { cwd: parentDir });

    res.writeHead(200, {
        'Content-Type': 'application/zip',
        'Content-Disposition': `attachment; filename="${filename}"`,
        'Cache-Control': 'no-cache'
    });

    zip.stdout.pipe(res);

    zip.stderr.on('data', (data) => {
        console.error(`Backup stderr: ${data}`);
    });

    zip.on('error', (err) => {
        console.error('Failed to start backup process:', err);
        if (!res.headersSent) {
            res.writeHead(500, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Failed to start backup process' }));
        }
    });

    zip.on('close', (code) => {
        if (code !== 0) {
            console.error(`Backup process exited with code ${code}`);
        }
    });

    req.on('close', () => {
        if (zip.exitCode === null) {
            zip.kill();
        }
    });
}

module.exports = { createBackup };
