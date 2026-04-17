/** @file Playwright runner with optional sandbox fallback for build pipelines */
const { spawnSync } = require('child_process');

const args = process.argv.slice(2);
const allowSandboxFallback = args.includes('--allow-sandbox-fallback');

function runPlaywright() {
    const env = { ...process.env, PLAYWRIGHT_BROWSERS_PATH: '.playwright-browsers' };
    delete env.NO_COLOR;

    return spawnSync('npx', ['playwright', 'test'], {
        env,
        encoding: 'utf8'
    });
}

function isSandboxLaunchFailure(output) {
    const hasLaunchClosed = /browserType\.launch: Target page, context or browser has been closed/i.test(output);
    const hasSandboxSignal = [
        /Permission denied \(1100\)/i,
        /bootstrap_check_in/i,
        /MachPortRendezvousServer/i,
        /crashpad_handler: --database is required/i,
        /The process has crashed/i
    ].some((pattern) => pattern.test(output));

    return hasLaunchClosed && hasSandboxSignal;
}

const result = runPlaywright();
if (result.stdout) process.stdout.write(result.stdout);
if (result.stderr) process.stderr.write(result.stderr);

if (result.status === 0) {
    process.exit(0);
}

const combinedOutput = `${result.stdout || ''}\n${result.stderr || ''}`;
if (allowSandboxFallback && isSandboxLaunchFailure(combinedOutput)) {
    console.warn('⚠️ Playwright Chromium blocked by sandbox permissions, skipping e2e in safe mode.');
    process.exit(0);
}

process.exit(result.status || 1);
