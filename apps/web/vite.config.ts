import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vitest/config';

export default defineConfig({
    plugins: [sveltekit()],
    server: {
        port: 4173,
    },
    preview: {
        port: 4174,
    },
    test: {
        environment: 'node',
        include: ['tests/unit/**/*.test.ts'],
    },
});
