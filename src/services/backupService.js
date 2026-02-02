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

/**
 * Restores the data folder from a zip backup
 * @param {import('http').IncomingMessage} req
 * @param {import('http').ServerResponse} res 
 */
async function restoreBackup(req, res) {
    const parentDir = path.dirname(DATA_DIR);
    const tempZipPath = path.join(parentDir, `temp_restore_${Date.now()}.zip`);
    const writeStream = fs.createWriteStream(tempZipPath);

    req.pipe(writeStream);

    writeStream.on('finish', () => {
        // 1. Back up current data just in case
        const oldDataDir = DATA_DIR + '_old_' + Date.now();

        try {
            if (fs.existsSync(DATA_DIR)) {
                fs.renameSync(DATA_DIR, oldDataDir);
            }

            // 2. Unzip
            // -o: overwrite files without prompting
            // -d: specify directory to extract to
            const unzip = spawn('unzip', ['-o', tempZipPath, '-d', parentDir]);

            unzip.on('close', (code) => {
                // Remove temp zip
                if (fs.existsSync(tempZipPath)) {
                    fs.unlinkSync(tempZipPath);
                }

                if (code === 0) {
                    // Success! Remove old data backup
                    if (fs.existsSync(oldDataDir)) {
                        spawn('rm', ['-rf', oldDataDir]);
                    }
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ success: true }));
                } else {
                    console.error(`Unzip exited with code ${code}`);
                    // Fail! Rollback
                    if (fs.existsSync(DATA_DIR)) {
                        spawn('rm', ['-rf', DATA_DIR]);
                    }
                    if (fs.existsSync(oldDataDir)) {
                        fs.renameSync(oldDataDir, DATA_DIR);
                    }
                    res.writeHead(500, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ error: 'Failed to extract backup. Is it a valid zip?' }));
                }
            });

            unzip.stderr.on('data', (data) => {
                console.error(`Unzip stderr: ${data}`);
            });

        } catch (err) {
            console.error('Restore error:', err);
            // Rollback if possible
            if (fs.existsSync(oldDataDir) && !fs.existsSync(DATA_DIR)) {
                try {
                    fs.renameSync(oldDataDir, DATA_DIR);
                } catch (e) { console.error('Rollback failed:', e); }
            }
            res.writeHead(500, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Restore failed: ' + err.message }));
        }
    });

    writeStream.on('error', (err) => {
        console.error('File write error:', err);
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Failed to save uploaded file' }));
    });
}

module.exports = { createBackup, restoreBackup };
