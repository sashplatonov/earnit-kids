const http = require('http');
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const PORT = 8080;
const PUBLIC_DIR = path.join(__dirname, 'public');

const mimeTypes = {
    '.html': 'text/html',
    '.js': 'text/javascript',
    '.css': 'text/css'
};

const server = http.createServer((req, res) => {
    let filePath = path.join(PUBLIC_DIR, req.url === '/' ? '/index.html' : req.url.split('?')[0]);
    if (!fs.existsSync(filePath)) {
        res.writeHead(404);
        res.end('Not found: ' + req.url);
        return;
    }
    const ext = path.extname(filePath);
    res.writeHead(200, { 'Content-Type': mimeTypes[ext] || 'text/plain' });
    res.end(fs.readFileSync(filePath));
});

server.listen(PORT, async () => {
    console.log(`Server listening on port ${PORT}`);
    const browser = await chromium.launch({ executablePath: process.env.PLAYWRIGHT_BROWSERS_PATH ? undefined : undefined });
    const page = await browser.newPage();
    page.on('console', msg => {
        if (msg.type() === 'error') console.log('PAGE ERROR LOG:', msg.text());
        else console.log('PAGE LOG:', msg.text());
    });
    page.on('pageerror', err => console.log('PAGE EXCEPTION:', err.message));

    await page.goto(`http://localhost:${PORT}/index.html`);
    await page.waitForTimeout(2000);
    await browser.close();
    server.close();
    process.exit(0);
});
