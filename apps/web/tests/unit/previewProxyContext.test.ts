import { describe, expect, it } from 'vitest';
import { buildProxyReferer, resolveProxyContext } from '../../scripts/proxy-context.mjs';

describe('resolveProxyContext', () => {
    it('keeps backend target and public origin separate', () => {
        const context = resolveProxyContext({
            BACKEND_URL: 'http://backend:8080///',
            APP_URL: 'http://localhost:5001///',
            PORT: '4174',
            HOST: '0.0.0.0',
        } as NodeJS.ProcessEnv);

        expect(context.backendOrigin).toBe('http://backend:8080');
        expect(context.backendUrl.origin).toBe('http://backend:8080');
        expect(context.publicOrigin).toBe('http://localhost:5001');
        expect(context.publicUrl.origin).toBe('http://localhost:5001');
    });

    it('falls back to the current preview host when no public origin override exists', () => {
        const context = resolveProxyContext({
            PORT: '4176',
            HOST: '127.0.0.1',
        } as NodeJS.ProcessEnv);

        expect(context.publicOrigin).toBe('http://127.0.0.1:4176');
    });
});

describe('buildProxyReferer', () => {
    it('rewrites referer to the configured public origin while preserving path and query', () => {
        expect(buildProxyReferer('http://127.0.0.1:4176/login.html?tab=register', 'http://localhost:5001')).toBe(
            'http://localhost:5001/login.html?tab=register'
        );
    });


            it('prefers APP_URL over stale PUBLIC_BASE_URL when both are present', () => {
                const context = resolveProxyContext({
                    BACKEND_URL: 'http://backend:8080',
                    PUBLIC_BASE_URL: 'http://localhost:3000',
                    APP_URL: 'http://localhost:5001',
                } as NodeJS.ProcessEnv);

                expect(context.publicOrigin).toBe('http://localhost:5001');
            });
    it('falls back to the public origin when referer is malformed', () => {
        expect(buildProxyReferer('not-a-url', 'http://localhost:5001')).toBe('http://localhost:5001');
    });
});