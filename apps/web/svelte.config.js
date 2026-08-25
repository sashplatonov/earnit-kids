import adapter from '@sveltejs/adapter-node';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

const config = {
    preprocess: vitePreprocess(),
    kit: {
        adapter: adapter({ precompress: false }),
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
