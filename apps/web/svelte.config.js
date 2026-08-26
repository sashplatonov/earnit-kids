import adapter from '@sveltejs/adapter-node';
import staticAdapter from '@sveltejs/adapter-static';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

const isStaticBuild = process.env.STATIC_BUILD === 'true';
const assetsDirectory = process.env.PUBLIC_SITE_ASSETS_DIR || 'static';

const config = {
    preprocess: vitePreprocess(),
    kit: {
        adapter: isStaticBuild
            ? staticAdapter({
                pages: 'build/static',
                assets: 'build/static',
                precompress: false,
                strict: false,
            })
            : adapter({ precompress: false }),
        files: {
            assets: assetsDirectory,
        },
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
                // Svelte components use dynamic CSS variables for responsive
                // controls such as the Telegram tab bar column count.
                'style-src-attr': ['unsafe-inline'],
                'font-src': ['self', 'https://fonts.gstatic.com'],
                'img-src': ['self', 'data:', 'https://t.me'],
                'connect-src': ['self'],
                'worker-src': ['self'],
                'manifest-src': ['self'],
            },
        },
    },
};

export default config;
