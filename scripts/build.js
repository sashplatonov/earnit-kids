const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const PUBLIC_DIR = path.join(__dirname, '../public');
const DIST_DIR = path.join(__dirname, '../public/dist');

// Ensure dist directory exists
if (!fs.existsSync(DIST_DIR)) {
    fs.mkdirSync(DIST_DIR, { recursive: true });
}

console.log('🚀 Starting production build...');

// 1. Minify JS modules
console.log('📦 Minifying JS modules...');
const jsModulesDir = path.join(PUBLIC_DIR, 'js/modules');
const destJsDir = path.join(DIST_DIR, 'js/modules');
if (!fs.existsSync(destJsDir)) fs.mkdirSync(destJsDir, { recursive: true });

const jsFiles = fs.readdirSync(jsModulesDir).filter(f => f.endsWith('.js'));
for (const file of jsFiles) {
    console.log(`  ⏳ Minifying ${file}...`);
    execSync(`npx terser ${path.join(jsModulesDir, file)} -o ${path.join(destJsDir, file)} --compress --mangle`);
}

// Minify other JS files
const otherJsFiles = ['config.js', 'super-admin.js'];
for (const file of otherJsFiles) {
    const src = path.join(PUBLIC_DIR, 'js', file);
    if (fs.existsSync(src)) {
        console.log(`  ⏳ Minifying ${file}...`);
        const destDir = path.join(DIST_DIR, 'js');
        if (!fs.existsSync(destDir)) fs.mkdirSync(destDir, { recursive: true });
        execSync(`npx terser ${src} -o ${path.join(destDir, file)} --compress --mangle`);
    }
}

// 2. Minify CSS
console.log('🎨 Minifying CSS...');
const cssDir = path.join(PUBLIC_DIR, 'css');
const destCssDir = path.join(DIST_DIR, 'css');
if (!fs.existsSync(destCssDir)) fs.mkdirSync(destCssDir, { recursive: true });

const cssFiles = fs.readdirSync(cssDir).filter(f => f.endsWith('.css'));
for (const file of cssFiles) {
    console.log(`  ⏳ Minifying ${file}...`);
    execSync(`npx cleancss -o ${path.join(destCssDir, file)} ${path.join(cssDir, file)}`);
}

console.log('✅ Build completed! Files are in public/dist');
console.log('💡 Note: Update your server to serve from public/dist in production.');
