import { describe, expect, it } from 'vitest';
import { loadAppConfig } from '../../src/lib/server/config';

describe('loadAppConfig', () => {
    it('returns the migration defaults', () => {
        const config = loadAppConfig({} as NodeJS.ProcessEnv);

        expect(config.backendOrigin).toBe('http://localhost:8080');
        expect(config.publicOrigin).toBe('http://localhost:3000');
        expect(config.sessionPath).toBe('/api/page-data/session');
        expect(config.wsPath).toBe('/ws');
        expect(config.devPort).toBe(4173);
        expect(config.previewPort).toBe(4174);
    });

    it('normalizes explicit overrides', () => {
        const config = loadAppConfig({
            BACKEND_ORIGIN: 'https://api.example.test///',
            PUBLIC_BASE_URL: 'https://app.example.test///',
            SESSION_PATH: '/session/bootstrap',
            WS_PATH: '/realtime',
            DEV_PORT: '5000',
            PREVIEW_PORT: '5001',
        } as NodeJS.ProcessEnv);

        expect(config.backendOrigin).toBe('https://api.example.test');
        expect(config.publicOrigin).toBe('https://app.example.test');
        expect(config.sessionPath).toBe('/session/bootstrap');
        expect(config.wsPath).toBe('/realtime');
        expect(config.devPort).toBe(5000);
        expect(config.previewPort).toBe(5001);
    });

    it('accepts compose-compatible runtime config', () => {
        const config = loadAppConfig({
            BACKEND_URL: 'http://backend:8080///',
            APP_URL: 'http://localhost:3001///',
        } as NodeJS.ProcessEnv);

        expect(config.backendOrigin).toBe('http://backend:8080');
        expect(config.publicOrigin).toBe('http://localhost:3001');
    });
});
