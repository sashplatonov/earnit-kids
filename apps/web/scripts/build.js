const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const PUBLIC_DIR = path.join(__dirname, '../public');
const DIST_DIR = path.join(__dirname, '../public/dist');

function ensureDir(dirPath) {
    if (!fs.existsSync(dirPath)) {
        fs.mkdirSync(dirPath, { recursive: true });
    }
}

function copyRecursive(srcDir, destDir) {
    ensureDir(destDir);
    for (const entry of fs.readdirSync(srcDir, { withFileTypes: true })) {
        const srcPath = path.join(srcDir, entry.name);
        const destPath = path.join(destDir, entry.name);
        if (entry.isDirectory()) {
            copyRecursive(srcPath, destPath);
            continue;
        }
        fs.copyFileSync(srcPath, destPath);
    }
}

// Ensure dist directory exists
ensureDir(DIST_DIR);

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
const otherJsFiles = ['config.js', 'super-admin.js', 'sw.js'];
for (const file of otherJsFiles) {
    const src = path.join(PUBLIC_DIR, 'js', file);
    const rootSrc = path.join(PUBLIC_DIR, file); // sw.js is at root
    const targetSrc = fs.existsSync(src) ? src : (fs.existsSync(rootSrc) ? rootSrc : null);

    if (targetSrc) {
        console.log(`  ⏳ Minifying ${file}...`);
        const relativeDir = path.dirname(path.relative(PUBLIC_DIR, targetSrc));
        const destDir = path.join(DIST_DIR, relativeDir);
        if (!fs.existsSync(destDir)) fs.mkdirSync(destDir, { recursive: true });
        execSync(`npx terser ${targetSrc} -o ${path.join(DIST_DIR, path.relative(PUBLIC_DIR, targetSrc))} --compress --mangle`);
    }
}

// 2. Minify CSS
console.log('🎨 Minifying CSS...');
const cssDir = path.join(PUBLIC_DIR, 'css');
const destCssDir = path.join(DIST_DIR, 'css');
const cssPartialsDir = path.join(cssDir, 'partials');
const destCssPartialsDir = path.join(destCssDir, 'partials');

if (!fs.existsSync(destCssDir)) fs.mkdirSync(destCssDir, { recursive: true });
if (!fs.existsSync(destCssPartialsDir)) fs.mkdirSync(destCssPartialsDir, { recursive: true });

// Bundle and minify main style.css (Step 17)
console.log('  ⏳ Bundling and minifying style.css...');
execSync(`npx cleancss -o ${path.join(destCssDir, 'style.css')} ${path.join(cssDir, 'style.css')}`);

// Minify other top-level CSS files
const otherCssFiles = fs.readdirSync(cssDir).filter(f => f.endsWith('.css') && f !== 'style.css');
for (const file of otherCssFiles) {
    console.log(`  ⏳ Minifying ${file}...`);
    execSync(`npx cleancss -o ${path.join(destCssDir, file)} ${path.join(cssDir, file)}`);
}

// Minify partials (to support direct imports if needed, though style.css bundles them)
if (fs.existsSync(cssPartialsDir)) {
    const partials = fs.readdirSync(cssPartialsDir).filter(f => f.endsWith('.css'));
    for (const file of partials) {
        console.log(`  ⏳ Minifying partials/${file}...`);
        execSync(`npx cleancss -o ${path.join(destCssPartialsDir, file)} ${path.join(cssPartialsDir, file)}`);
    }
}

// 3. Copy static assets required by HTML/PWA surfaces
console.log('🖼️ Copying static assets...');
for (const file of ['manifest.json', 'favicon.ico']) {
    const srcPath = path.join(PUBLIC_DIR, file);
    if (!fs.existsSync(srcPath)) continue;
    console.log(`  ⏳ Copying ${file}...`);
    fs.copyFileSync(srcPath, path.join(DIST_DIR, file));
}

const imgSrcDir = path.join(PUBLIC_DIR, 'img');
if (fs.existsSync(imgSrcDir)) {
    console.log('  ⏳ Copying img assets...');
    copyRecursive(imgSrcDir, path.join(DIST_DIR, 'img'));
}

console.log('✅ Build completed! Files are in public/dist');
console.log('💡 Note: Update your server to serve from public/dist in production.');
