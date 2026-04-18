import { describe, expect, it } from 'vitest';
import { loadAppConfig } from '../../src/lib/server/config';

describe('loadAppConfig', () => {
    it('returns the migration defaults', () => {
        const config = loadAppConfig({} as NodeJS.ProcessEnv);

        expect(config.backendOrigin).toBe('http://localhost:8080');
        expect(config.sessionPath).toBe('/api/page-data/session');
        expect(config.wsPath).toBe('/ws');
        expect(config.legacyWebOrigin).toBe('http://localhost:3000');
        expect(config.devPort).toBe(4173);
        expect(config.previewPort).toBe(4174);
    });

    it('normalizes explicit overrides', () => {
        const config = loadAppConfig({
            BACKEND_ORIGIN: 'https://api.example.test///',
            SESSION_PATH: '/session/bootstrap',
            WS_PATH: '/realtime',
            LEGACY_WEB_ORIGIN: 'https://legacy.example.test/',
            DEV_PORT: '5000',
            PREVIEW_PORT: '5001',
        } as NodeJS.ProcessEnv);

        expect(config.backendOrigin).toBe('https://api.example.test');
        expect(config.sessionPath).toBe('/session/bootstrap');
        expect(config.wsPath).toBe('/realtime');
        expect(config.legacyWebOrigin).toBe('https://legacy.example.test');
        expect(config.devPort).toBe(5000);
        expect(config.previewPort).toBe(5001);
    });
});
