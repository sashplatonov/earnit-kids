const fs = require('fs');
const path = require('path');

// Read package.json to get app version
const packageJsonPath = path.join(__dirname, 'package.json');
const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'));
const APP_VERSION = packageJson.version;

console.log('APP_VERSION:', APP_VERSION);

// Test replacement
let html = '<div class="header__version" style="font-size: 0.8rem; color: #666; margin-top: 5px;">Версия: {{APP_VERSION}}</div>';
html = html.replace(/\{\{APP_VERSION\}\}/g, APP_VERSION);
console.log('After replacement:', html);