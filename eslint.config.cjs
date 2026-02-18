const STRICT_RULES = {
    complexity: ['error', 20],
    'max-lines': ['error', { max: 500, skipBlankLines: true, skipComments: true }],
    'max-lines-per-function': ['error', { max: 120, skipBlankLines: true, skipComments: true, IIFEs: true }],
    'max-depth': ['error', 4],
    'max-params': ['error', 5],
    'max-statements': ['error', 60],
};

/** @type {import('eslint').Linter.FlatConfig[]} */
module.exports = [
    {
        linterOptions: {
            reportUnusedDisableDirectives: 'error',
        },
        ignores: [
            'node_modules/**',
            'data/**',
            'data.json/**',
            'migrations/**',
            'src/templates/**',
        ],
    },
    {
        files: ['src/**/*.js', 'scripts/**/*.js', 'tests/**/*.js', 'test_version.js'],
        languageOptions: {
            ecmaVersion: 2022,
            sourceType: 'commonjs',
        },
        rules: {
            ...STRICT_RULES,
        },
    },
    {
        files: ['public/js/**/*.js'],
        languageOptions: {
            ecmaVersion: 2022,
            sourceType: 'module',
            globals: {
                window: 'readonly',
                document: 'readonly',
                fetch: 'readonly',
                localStorage: 'readonly',
                sessionStorage: 'readonly',
                navigator: 'readonly',
                URL: 'readonly',
                FormData: 'readonly',
                confirm: 'readonly',
                alert: 'readonly',
                setTimeout: 'readonly',
                clearTimeout: 'readonly',
                setInterval: 'readonly',
                clearInterval: 'readonly',
                location: 'readonly',
            },
        },
        rules: {
            ...STRICT_RULES,
        },
    },
];
