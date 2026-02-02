const { spawn, spawnSync } = require('child_process');
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
        return res.end(JSON.stringify({ error: 'Data folder not found at ' + DATA_DIR }));
    }

    const files = fs.readdirSync(DATA_DIR);
    if (files.length === 0) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({ error: 'Data folder is empty' }));
    }

    const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
    const parentDir = path.dirname(DATA_DIR);
    const dataFolderName = path.basename(DATA_DIR);

    // Try zip first, then fallback to tar
    let tool = 'zip';
    let args = ['-r', '-', dataFolderName];
    let extension = 'zip';
    let mimeType = 'application/zip';

    try {
        const { execSync } = require('child_process');
        execSync('zip -v');
    } catch (err) {
        console.log('Zip not found, trying tar...');
        try {
            const { execSync } = require('child_process');
            execSync('tar --version');
            tool = 'tar';
            args = ['-cz', dataFolderName];
            extension = 'tar.gz';
            mimeType = 'application/gzip';
        } catch (tarErr) {
            console.error('Neither zip nor tar found');
            res.writeHead(500, { 'Content-Type': 'application/json' });
            return res.end(JSON.stringify({
                error: 'System backup utilities (zip/tar) not found.',
                details: 'Please install zip or tar on the server.'
            }));
        }
    }

    const filename = `backup-data-${timestamp}.${extension}`;
    console.log(`Starting backup using ${tool} for ${dataFolderName} in ${parentDir}`);

    const proc = spawn(tool, args, { cwd: parentDir });

    let headersSent = false;

    proc.stdout.on('data', (chunk) => {
        if (!headersSent) {
            res.writeHead(200, {
                'Content-Type': mimeType,
                'Content-Disposition': `attachment; filename="${filename}"`,
                'Cache-Control': 'no-cache'
            });
            headersSent = true;
        }
        res.write(chunk);
    });

    proc.stderr.on('data', (data) => {
        console.error(`Backup ${tool} stderr: ${data}`);
    });

    proc.on('error', (err) => {
        console.error(`Failed to start ${tool} process:`, err);
        if (!headersSent) {
            res.writeHead(500, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: `Failed to start backup process: ${err.message}` }));
        } else {
            res.end();
        }
    });

    proc.on('close', (code) => {
        console.log(`Backup process (${tool}) exited with code ${code}`);
        if (code !== 0) {
            if (!headersSent) {
                res.writeHead(500, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: `Backup failed with exit code ${code}` }));
            } else {
                res.end();
            }
        } else {
            if (!headersSent) {
                res.writeHead(500, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Backup produced no data' }));
            } else {
                res.end();
            }
        }
    });

    req.on('close', () => {
        if (proc.exitCode === null) {
            proc.kill();
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

    writeStream.on('finish', async () => {
        const oldDataDir = DATA_DIR + '_old_' + Date.now();
        let backupCreated = false;
        let dataDirWasRenamed = false;

        try {
            if (fs.existsSync(DATA_DIR)) {
                try {
                    // Try to rename the whole directory (fastest if on same device)
                    fs.renameSync(DATA_DIR, oldDataDir);
                    backupCreated = true;
                    dataDirWasRenamed = true;
                } catch (err) {
                    if (err.code === 'EXDEV' || err.code === 'EBUSY') {
                        // Cross-device or mount point error. Fallback to copy and clean.
                        console.log('Cross-device restore detected, falling back to copy...');
                        fs.mkdirSync(oldDataDir, { recursive: true });
                        const cp = spawnSync('cp', ['-r', path.join(DATA_DIR, '.'), oldDataDir]);
                        if (cp.status !== 0) {
                            throw new Error('Failed to copy current data for backup');
                        }
                        backupCreated = true;

                        // Clear current data directory contents instead of deleting the directory (mount point)
                        const files = fs.readdirSync(DATA_DIR);
                        for (const file of files) {
                            fs.rmSync(path.join(DATA_DIR, file), { recursive: true, force: true });
                        }
                    } else {
                        throw err;
                    }
                }
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
                    rollback(DATA_DIR, oldDataDir, dataDirWasRenamed);
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
            rollback(DATA_DIR, oldDataDir, dataDirWasRenamed);
            res.writeHead(500, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Restore failed: ' + err.message }));
        }
    });

    /**
     * Helper to rollback changes in case of failure
     */
    function rollback(dataDir, oldDir, wasRenamed) {
        if (!fs.existsSync(oldDir)) return;

        try {
            if (wasRenamed) {
                // If we successfully renamed the original dir, just rename it back
                if (fs.existsSync(dataDir)) {
                    spawnSync('rm', ['-rf', dataDir]);
                }
                fs.renameSync(oldDir, dataDir);
            } else {
                // If we copied contents, copy them back
                const files = fs.readdirSync(dataDir);
                for (const file of files) {
                    fs.rmSync(path.join(dataDir, file), { recursive: true, force: true });
                }
                spawnSync('cp', ['-r', path.join(oldDir, '.'), dataDir]);
            }
        } catch (e) {
            console.error('Rollback failed:', e);
        }
    }

    writeStream.on('error', (err) => {
        console.error('File write error:', err);
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Failed to save uploaded file' }));
    });
}

module.exports = { createBackup, restoreBackup };
