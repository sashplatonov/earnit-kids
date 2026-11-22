import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vitest/config';
import adapter from '@sveltejs/adapter-node';
import staticAdapter from '@sveltejs/adapter-static';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

const isStaticBuild = process.env.STATIC_BUILD === 'true';
const assetsDirectory = process.env.PUBLIC_SITE_ASSETS_DIR || 'static';
const adapterConfig = {
    pages: 'build/static',
    assets: 'build/static',
    precompress: false,
    strict: false,
};

function buildTimestamp(): string {
    // Format: YYYYMMDD-HHmm in CET (Europe/Berlin)
    const now = new Date();
    const [datePart, hh, mm] = now
        .toLocaleString('sv', { timeZone: 'Europe/Berlin' })
        .replace('T', ' ')
        .split(/[\s:]/);
    const compact = (datePart ?? '').replace(/-/g, '');
    return `${compact}-${(hh ?? '').padStart(2, '0')}${(mm ?? '').padStart(2, '0')}`;
}

export default defineConfig({
    plugins: [sveltekit({
        preprocess: vitePreprocess(),
        adapter: isStaticBuild ? staticAdapter(adapterConfig) : adapter({ precompress: false }),
        files: { assets: assetsDirectory },
        csp: {
            mode: 'auto',
            directives: {
                'default-src': ['self'],
                'base-uri': ['self'],
                'object-src': ['none'],
                'frame-ancestors': ['none'],
                'form-action': ['self'],
                'script-src': ['self', 'https://telegram.org'],
                'style-src': ['self', 'https://fonts.googleapis.com'],
                'style-src-attr': ['unsafe-inline'],
                'font-src': ['self', 'https://fonts.gstatic.com'],
                'img-src': ['self', 'data:', 'https://t.me'],
                'connect-src': ['self'],
                'worker-src': ['self'],
                'manifest-src': ['self'],
            },
        },
    })],
    define: {
        __BUILD_TS__: JSON.stringify(buildTimestamp()),
    },
    build: {
        rolldownOptions: {
            checks: {
                pluginTimings: false,
            },
        },
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
