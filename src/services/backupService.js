const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');
const { Pool } = require('pg');

/**
 * Creates a PostgreSQL backup using pg_dump and streams it to the response
 * @param {import('http').IncomingMessage} req
 * @param {import('http').ServerResponse} res 
 */
function createBackup(req, res) {
    const dbUrl = process.env.DATABASE_URL;
    if (!dbUrl) {
        res.writeHead(500, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({ error: 'DATABASE_URL is not configured' }));
    }

    const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
    const filename = `backup-pg-${timestamp}.dump`;

    // pg_dump -F c -d [url] (Custom format, compressed)
    const args = ['-F', 'c', dbUrl];

    console.log(`Starting DB backup...`);

    let headersSent = false;
    let finished = false;

    const proc = spawn('pg_dump', args);

    proc.stdout.on('data', (chunk) => {
        if (!headersSent && !finished) {
            res.writeHead(200, {
                'Content-Type': 'application/octet-stream',
                'Content-Disposition': `attachment; filename="${filename}"`,
                'Cache-Control': 'no-cache'
            });
            headersSent = true;
        }
        if (!finished) res.write(chunk);
    });

    proc.stderr.on('data', (data) => {
        console.error(`pg_dump stderr: ${data}`);
    });

    proc.on('error', (err) => {
        if (finished) return;
        finished = true;
        console.error(`Failed to start pg_dump:`, err);

        const isEnoent = err.code === 'ENOENT';
        const msg = isEnoent
            ? 'Инструмент pg_dump не установлен на сервере. Пожалуйста, установите postgresql-client.'
            : `Ошибка запуска процесса бэкапа: ${err.message}`;

        if (!headersSent) {
            res.writeHead(500, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: msg, code: err.code }));
        } else {
            res.end();
        }
    });

    proc.on('close', (code) => {
        if (finished) return;
        finished = true;
        console.log(`pg_dump exited with code ${code}`);

        if (code !== 0) {
            if (!headersSent) {
                res.writeHead(500, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: `Бэкап завершился с ошибкой (код ${code})` }));
            } else {
                res.end();
            }
        } else {
            if (!headersSent) {
                res.writeHead(500, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Бэкап не произвел данных' }));
            } else {
                res.end();
            }
        }
    });
}

/**
 * Restores the PostgreSQL database from a uploaded dump file using pg_restore
 * @param {import('http').IncomingMessage} req
 * @param {import('http').ServerResponse} res 
 */
function restoreBackup(req, res) {
    const dbUrl = process.env.DATABASE_URL;
    if (!dbUrl) {
        res.writeHead(500, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({ error: 'DATABASE_URL is not configured' }));
    }

    const tempPath = path.join(process.cwd(), `temp_restore_${Date.now()}.dump`);
    const writeStream = fs.createWriteStream(tempPath);

    req.pipe(writeStream);

    writeStream.on('finish', () => {
        console.log('Backup file uploaded, starting restore...');

        const args = ['-d', dbUrl, '--clean', '--if-exists', '--no-owner', tempPath];
        const proc = spawn('pg_restore', args);
        let finished = false;

        proc.stderr.on('data', (data) => {
            console.error(`pg_restore stderr: ${data}`);
        });

        proc.on('error', (err) => {
            if (finished) return;
            finished = true;
            console.error('Failed to start pg_restore:', err);
            cleanup();

            const isEnoent = err.code === 'ENOENT';
            const msg = isEnoent
                ? 'Инструмент pg_restore не установлен на сервере. Пожалуйста, установите postgresql-client.'
                : 'Ошибка запуска процесса восстановления';

            res.writeHead(500, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: msg, code: err.code }));
        });

        proc.on('close', (code) => {
            if (finished) return;
            finished = true;
            console.log(`pg_restore exited with code ${code}`);
            cleanup();

            if (code === 0 || code === 1) {
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: true }));
            } else {
                res.writeHead(500, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: `Restore failed with exit code ${code}` }));
            }
        });
    });

    writeStream.on('error', (err) => {
        console.error('File write error:', err);
        cleanup();
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Failed to upload backup file' }));
    });

    function cleanup() {
        if (fs.existsSync(tempPath)) {
            fs.unlinkSync(tempPath);
        }
    }
}

/**
 * Copies the current database to the reserve database
 * @param {import('http').IncomingMessage} req
 * @param {import('http').ServerResponse} res 
 */
function copyToReserve(req, res) {
    const dbUrl = process.env.DATABASE_URL;
    const reserveDbUrl = process.env.RESERVE_DATABASE_URL;

    if (!dbUrl || !reserveDbUrl) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({
            error: 'DATABASE_URL or RESERVE_DATABASE_URL is not configured'
        }));
    }

    console.log('Starting DB copy to reserve...');

    let finished = false;
    const dump = spawn('pg_dump', ['-F', 'c', dbUrl]);
    const restore = spawn('pg_restore', ['-d', reserveDbUrl, '--clean', '--if-exists', '--no-owner']);

    dump.stdout.pipe(restore.stdin);

    let dumpError = '';
    let restoreError = '';

    dump.stderr.on('data', (d) => dumpError += d.toString());
    restore.stderr.on('data', (d) => restoreError += d.toString());

    dump.on('error', (err) => {
        if (finished) return;
        finished = true;
        console.error('Dump error:', err);

        const isEnoent = err.code === 'ENOENT';
        const msg = isEnoent
            ? 'Инструмент pg_dump не установлен на сервере.'
            : 'Ошибка процесса дампа';

        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: msg, code: err.code }));
        restore.kill();
    });

    restore.on('error', (err) => {
        if (finished) return;
        finished = true;
        console.error('Restore error:', err);

        const isEnoent = err.code === 'ENOENT';
        const msg = isEnoent
            ? 'Инструмент pg_restore не установлен на сервере.'
            : 'Ошибка процесса восстановления';

        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: msg, code: err.code }));
        dump.kill();
    });

    restore.on('close', (code) => {
        if (finished) return;
        finished = true;
        console.log(`Copy process finished. Restore exit code: ${code}`);
        if (code === 0 || code === 1) {
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: true }));
        } else {
            res.writeHead(500, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({
                error: `Копирование не удалось (код ${code})`,
                details: { dumpError, restoreError }
            }));
        }
    });
}

/**
 * Checks if the reserve database is configured and accessible
 */
async function checkReserveDbConnection() {
    const url = process.env.RESERVE_DATABASE_URL;
    if (!url) {
        return { success: false, error: 'RESERVE_DATABASE_URL is not set in .env' };
    }

    // Try to connect directly
    const testPool = new Pool({
        connectionString: url,
        connectionTimeoutMillis: 5000,
        ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : undefined
    });

    try {
        await testPool.query('SELECT 1');
        await testPool.end();
        return { success: true };
    } catch (err) {
        await testPool.end().catch(() => { });

        // If "database does not exist", it means the host/credentials are correct but the name is wrong
        // Let's try to verify if the server itself is reachable by connecting to 'postgres' DB
        try {
            const baseUrl = url.substring(0, url.lastIndexOf('/') + 1) + 'postgres';
            const basePool = new Pool({
                connectionString: baseUrl,
                connectionTimeoutMillis: 3000,
                ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : undefined
            });
            await basePool.query('SELECT 1');
            await basePool.end();
            return {
                success: false,
                error: `БД не найдена, но сервер доступен. Ошибка: ${err.message}. Проверьте название БД в URL.`
            };
        } catch (baseErr) {
            return {
                success: false,
                error: `Не удалось подключиться к серверу БД: ${err.message}. Проверьте хост, порт и Docker-окружение.`
            };
        }
    }
}

module.exports = { createBackup, restoreBackup, copyToReserve, checkReserveDbConnection };
