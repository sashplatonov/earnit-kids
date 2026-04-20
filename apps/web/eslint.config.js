import js from '@eslint/js';
import globals from 'globals';
import svelte from 'eslint-plugin-svelte';
import tseslint from 'typescript-eslint';

export default tseslint.config(
    {
        ignores: ['.svelte-kit/**', 'build/**', 'coverage/**', 'package-lock.json', 'playwright-report/**', 'test-results/**'],
    },
    js.configs.recommended,
    ...tseslint.configs.recommended,
    ...svelte.configs['flat/recommended'],
    {
        languageOptions: {
            globals: {
                ...globals.browser,
                ...globals.node,
                __BUILD_TS__: 'readonly',
            },
        },
        rules: {
            // Project does not use SvelteKit i18n resolve() — disable href restriction
            'svelte/no-navigation-without-resolve': ['error', { ignoreLinks: true }],
            // Blog article pages render sanitized markdown from trusted internal files
            'svelte/no-at-html-tags': 'off',
        },
    },
    {
        files: ['**/*.svelte'],
        languageOptions: {
            parserOptions: {
                parser: tseslint.parser,
            },
        },
        rules: {
            // typescript-eslint crashes on certain type aliases in Svelte script blocks
            '@typescript-eslint/no-unused-vars': 'off',
        },
    },
);
