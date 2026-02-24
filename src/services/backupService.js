/** @file Backup Service business services */
const { exec } = require('child_process');
const path = require('path');
const fs = require('fs');
const os = require('os');
const { Client } = require('pg');
const config = require('../config');
const logger = require('../utils/logger');
const { sendTelegramDocument } = require('../utils/alerts');
const { sendJSON } = require('../utils/controllerUtils');

function ensureBackupDir() {
    const backupDir = path.join(config.DATA_DIR, 'backups');
    if (!fs.existsSync(backupDir)) {
        fs.mkdirSync(backupDir, { recursive: true });
    }
    return backupDir;
}

function readRawRequestBody(req) {
    return new Promise((resolve, reject) => {
        const chunks = [];
        req.on('data', (chunk) => chunks.push(Buffer.from(chunk)));
        req.on('end', () => resolve(Buffer.concat(chunks)));
        req.on('error', reject);
    });
}

/**
 * Perform a database backup and send it to Telegram
 */
async function performBackup() {
    if (!config.TELEGRAM.ENABLED) {
        logger.debug('Backup skipped: Telegram alerts not enabled');
        return;
    }

    const dbUrl = process.env.DATABASE_URL;
    if (!dbUrl) {
        logger.warn('Backup skipped: DATABASE_URL not set');
        return;
    }

    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    let backupDir;

    try {
        backupDir = ensureBackupDir();
    } catch (err) {
        logger.error({ err: err.message }, 'Failed to create backup directory');
        return;
    }

    const filename = `backup-${timestamp}.sql`;
    const filepath = path.join(backupDir, filename);

    // Command to create a backup using pg_dump
    // We use the full connection string
    const command = `pg_dump "${dbUrl}" -F c -f "${filepath}"`;

    logger.info({ filename }, 'Starting database backup...');

    exec(command, async (error, stdout, stderr) => {
        if (error) {
            logger.error({ error: error.message, stderr }, 'Backup process failed');
            return;
        }

        logger.info('Backup file created successfully, sending to Telegram...');
        const caption = `📦 <b>Database Backup</b>\n\n<b>Env:</b> ${config.env}\n<b>Date:</b> ${new Date().toLocaleString()}\n<b>File:</b> <code>${filename}</code>`;

        try {
            const success = await sendTelegramDocument(filepath, caption, { silent: true });

            if (success) {
                logger.info('Backup successfully sent to Telegram');
                // We keep the local file for now as a double backup, 
                // but we could delete it if storage is an issue.
            } else {
                logger.warn('Failed to send backup to Telegram');
            }
        } catch (sendErr) {
            logger.error({ err: sendErr.message }, 'Error during backup sending');
        }

        // Cleanup old local backups (keep only last 5)
        cleanupOldBackups(backupDir);
    });
}

/**
 * Remove old backup files to save space
 * @param {string} backupDir 
 */
function cleanupOldBackups(backupDir) {
    try {
        const files = fs.readdirSync(backupDir)
            .filter(f => f.startsWith('backup-'))
            .map(f => ({
                name: f,
                path: path.join(backupDir, f),
                time: fs.statSync(path.join(backupDir, f)).mtime.getTime()
            }))
            .sort((a, b) => b.time - a.time); // Newest first

        if (files.length > 5) {
            const extraFiles = files.slice(5);
            for (const file of extraFiles) {
                fs.unlinkSync(file.path);
                logger.debug({ file: file.name }, 'Old backup file removed');
            }
        }
    } catch (err) {
        logger.warn({ err: err.message }, 'Failed to cleanup old backups');
    }
}

let backupInterval = null;

/**
 * Initialize the backup scheduler
 */
function initBackupService() {
    // If backups are disabled, don't start the service
    if (!config.TELEGRAM.ENABLED) {
        return;
    }

    const intervalHours = parseInt(process.env.BACKUP_INTERVAL_HOURS, 10) || 24;
    const intervalMs = intervalHours * 60 * 60 * 1000;

    if (backupInterval) {
        clearInterval(backupInterval);
    }

    // Schedule periodic backups
    backupInterval = setInterval(performBackup, intervalMs);

    // Optional: run a backup shortly after startup if it's the first time
    // setTimeout(performBackup, 5 * 60 * 1000); // 5 minutes after startup

    logger.info({ intervalHours }, 'Database backup service initialized');
}

function createBackup(req, res) {
    const dbUrl = process.env.DATABASE_URL;
    if (!dbUrl) {
        return sendJSON(res, { success: false, error: 'DATABASE_URL not set' }, 500);
    }

    let backupDir;
    try {
        backupDir = ensureBackupDir();
    } catch (err) {
        logger.error({ err: err.message }, 'Failed to create backup directory');
        return sendJSON(res, { success: false, error: 'Failed to create backup directory' }, 500);
    }

    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    const filename = `backup-${timestamp}.dump`;
    const filepath = path.join(backupDir, filename);
    const command = `pg_dump "${dbUrl}" -F c -f "${filepath}"`;

    exec(command, (error, stdout, stderr) => {
        if (error) {
            logger.error({ error: error.message, stderr }, 'Backup creation failed');
            return sendJSON(res, { success: false, error: 'Backup failed' }, 500);
        }

        try {
            const stat = fs.statSync(filepath);
            res.writeHead(200, {
                'Content-Type': 'application/octet-stream',
                'Content-Disposition': `attachment; filename="${filename}"`,
                'Content-Length': stat.size
            });
            const stream = fs.createReadStream(filepath);
            stream.on('error', (streamErr) => {
                logger.error({ err: streamErr.message }, 'Backup stream failed');
                if (!res.headersSent) {
                    sendJSON(res, { success: false, error: 'Failed to stream backup' }, 500);
                } else {
                    res.end();
                }
            });
            stream.on('close', () => {
                fs.unlink(filepath, () => {});
            });
            stream.pipe(res);
        } catch (err) {
            logger.error({ err: err.message }, 'Failed to send backup file');
            sendJSON(res, { success: false, error: 'Failed to send backup file' }, 500);
        }
    });
}

async function restoreBackup(req, res) {
    const dbUrl = process.env.DATABASE_URL;
    if (!dbUrl) {
        return sendJSON(res, { success: false, error: 'DATABASE_URL not set' }, 500);
    }

    let rawDump;
    try {
        rawDump = await readRawRequestBody(req);
    } catch (err) {
        logger.error({ err: err.message }, 'Failed to read restore payload');
        return sendJSON(res, { success: false, error: 'Invalid request body' }, 400);
    }

    if (!rawDump || rawDump.length === 0) {
        return sendJSON(res, { success: false, error: 'Backup file is empty' }, 400);
    }

    const dumpFile = path.join(os.tmpdir(), `restore-${Date.now()}.dump`);
    try {
        fs.writeFileSync(dumpFile, rawDump);
    } catch (err) {
        logger.error({ err: err.message }, 'Failed to write temporary restore file');
        return sendJSON(res, { success: false, error: 'Failed to process file' }, 500);
    }

    const command = `pg_restore --clean --if-exists --no-owner --no-privileges -d "${dbUrl}" "${dumpFile}"`;
    exec(command, (error, stdout, stderr) => {
        fs.unlink(dumpFile, () => {});
        if (error) {
            logger.error({ error: error.message, stderr }, 'Restore failed');
            return sendJSON(res, { success: false, error: 'Restore failed' }, 500);
        }
        return sendJSON(res, { success: true });
    });
}

function copyToReserve(req, res) {
    const sourceDbUrl = process.env.DATABASE_URL;
    const reserveDbUrl = process.env.RESERVE_DATABASE_URL;

    if (!sourceDbUrl || !reserveDbUrl) {
        return sendJSON(res, { success: false, error: 'DATABASE_URL or RESERVE_DATABASE_URL not set' }, 500);
    }

    const command = `pg_dump "${sourceDbUrl}" -F c | pg_restore --clean --if-exists --no-owner --no-privileges -d "${reserveDbUrl}"`;
    exec(command, (error, stdout, stderr) => {
        if (error) {
            logger.error({ error: error.message, stderr }, 'Copy to reserve failed');
            return sendJSON(res, { success: false, error: 'Copy to reserve failed' }, 500);
        }
        return sendJSON(res, { success: true });
    });
}

async function checkReserveDbConnection() {
    const reserveDbUrl = process.env.RESERVE_DATABASE_URL;
    if (!reserveDbUrl) {
        return { success: false, error: 'RESERVE_DATABASE_URL not set' };
    }

    const client = new Client({ connectionString: reserveDbUrl });
    try {
        await client.connect();
        await client.query('SELECT 1');
        return { success: true };
    } catch (err) {
        logger.warn({ err: err.message }, 'Reserve DB connection check failed');
        return { success: false, error: 'Reserve DB unavailable' };
    } finally {
        await client.end().catch(() => {});
    }
}

module.exports = {
    createBackup,
    restoreBackup,
    copyToReserve,
    checkReserveDbConnection,
    performBackup,
    initBackupService
};
