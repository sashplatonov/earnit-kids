import type { AppConfig } from '$lib/types/config';

const DEFAULT_BACKEND_ORIGIN = 'http://localhost:8080';
const DEFAULT_SESSION_PATH = '/api/page-data/session';
const DEFAULT_WS_PATH = '/ws';
const DEFAULT_DEV_PORT = 4173;
const DEFAULT_PREVIEW_PORT = 4174;

function trimTrailingSlashes(value: string): string {
    return value.replace(/\/+$/, '');
}

function parsePort(rawValue: string | undefined, fallbackValue: number): number {
    const numericValue = Number(rawValue);
    return Number.isFinite(numericValue) && numericValue > 0 ? numericValue : fallbackValue;
}

export function loadAppConfig(env: NodeJS.ProcessEnv = process.env): AppConfig {
    const backendOrigin = env.BACKEND_ORIGIN || env.BACKEND_URL || DEFAULT_BACKEND_ORIGIN;

    return {
        backendOrigin: trimTrailingSlashes(backendOrigin),
        sessionPath: env.SESSION_PATH || DEFAULT_SESSION_PATH,
        wsPath: env.WS_PATH || DEFAULT_WS_PATH,
        devPort: parsePort(env.DEV_PORT, DEFAULT_DEV_PORT),
        previewPort: parsePort(env.PREVIEW_PORT, DEFAULT_PREVIEW_PORT),
    };
}
