import adapter from '@sveltejs/adapter-static';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

const config = {
    preprocess: vitePreprocess(),
    kit: {
        adapter: adapter({
            pages: 'build/static',
            assets: 'build/static',
            precompress: false,
            strict: false,
        }),
    },
};

export default config;
