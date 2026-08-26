import { cp, mkdtemp, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const projectDirectory = path.resolve(scriptDirectory, '..');
const sourceAssetsDirectory = path.join(projectDirectory, 'static');
const viteEntryPoint = path.join(projectDirectory, 'node_modules/vite/bin/vite.js');

function run(command, args, environment) {
    return new Promise((resolve, reject) => {
        const child = spawn(command, args, {
            cwd: projectDirectory,
            env: environment,
            stdio: 'inherit',
        });
        child.on('error', reject);
        child.on('exit', (code, signal) => {
            if (code === 0) {
                resolve();
                return;
            }
            reject(new Error(`${path.basename(command)} ${args.join(' ')} exited with ${signal || code}`));
        });
    });
}

const stagingDirectory = await mkdtemp(path.join(os.tmpdir(), 'earnit-kids-web-'));
const stagedAssetsDirectory = path.join(stagingDirectory, 'static');

try {
    await cp(sourceAssetsDirectory, stagedAssetsDirectory, { recursive: true });
    const environment = {
        ...process.env,
        PUBLIC_OUTPUT_DIR: path.join(stagedAssetsDirectory, 'public'),
        PUBLIC_SITE_ASSETS_DIR: stagedAssetsDirectory,
    };

    await run(process.execPath, [path.join(scriptDirectory, 'public-site/generate.mjs')], environment);
    await run(process.execPath, [viteEntryPoint, 'build'], environment);
} finally {
    await rm(stagingDirectory, { recursive: true, force: true });
}
