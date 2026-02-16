const fs = require('fs');
const path = require('path');
const { getBuildVersion } = require('./src/utils/buildVersion');

// Read package.json to get app version
const packageJsonPath = path.join(__dirname, 'package.json');
const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'));
const APP_VERSION = packageJson.version;
const BUILD_VERSION = getBuildVersion();

console.log('APP_VERSION:', APP_VERSION);
console.log('BUILD_VERSION:', BUILD_VERSION);

const html = '<div class="header__version" style="font-size: 0.8rem; color: #666; margin-top: 5px;"><span class="header__version-label">Версия: {{APP_VERSION}}</span><span class="header__build-label">Сборка {{BUILD_VERSION}}</span></div>';
const filled = html
    .replace(/\{\{APP_VERSION\}\}/g, APP_VERSION)
    .replace(/\{\{BUILD_VERSION\}\}/g, BUILD_VERSION);

console.log('After replacement:', filled);
