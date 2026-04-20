import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vitest/config';

function buildTimestamp(): string {
    // Format: YYYYMMDD-HHmm in CET (Europe/Berlin)
    const now = new Date();
    // 'sv' locale returns "YYYY-MM-DD HH:mm:ss" in the given timezone
    const parts = now.toLocaleString('sv', { timeZone: 'Europe/Berlin' }).split(/[ :]/);
    // parts: [YYYY-MM-DD, HH, mm, ss] — split on space then colon
    const [datePart, hh, mm] = now
        .toLocaleString('sv', { timeZone: 'Europe/Berlin' })
        .replace('T', ' ')
        .split(/[\s:]/);
    const compact = (datePart ?? '').replace(/-/g, '');
    return `${compact}-${(hh ?? '').padStart(2, '0')}${(mm ?? '').padStart(2, '0')}`;
}

export default defineConfig({
    plugins: [sveltekit()],
    define: {
        __BUILD_TS__: JSON.stringify(buildTimestamp()),
    },
    server: {
        port: 4173,
    },
    preview: {
        port: 4174,
    },
    test: {
        environment: 'node',
        include: ['tests/unit/**/*.test.ts'],
        coverage: {
            provider: 'v8',
            reporter: ['text', 'html'],
            thresholds: {
                lines: 80,
                functions: 80,
                branches: 80,
                statements: 80,
            },
        },
    },
});
