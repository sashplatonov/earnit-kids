const { exec } = require('child_process');
const path = require('path');
const fs = require('fs');
const config = require('../config');
const logger = require('../utils/logger');
const { sendTelegramDocument } = require('../utils/alerts');

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
    const backupDir = path.join(config.DATA_DIR, 'backups');

    try {
        if (!fs.existsSync(backupDir)) {
            fs.mkdirSync(backupDir, { recursive: true });
        }
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
            const success = await sendTelegramDocument(filepath, caption);

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

module.exports = {
    performBackup,
    initBackupService
};
