import type { AppConfig } from '$lib/types/config';

const DEFAULT_BACKEND_ORIGIN = 'http://localhost:8080';
const DEFAULT_PUBLIC_ORIGIN = 'http://localhost:4174';
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

// EXPLAIN: Public links (Mini App footer, sitemap, robots, proxy referer) must
// EXPLAIN: point at the site root, never at a specific app page. Strip any path
// EXPLAIN: and query so APP_URL like https://host/en/app/tasks yields https://host.
function resolvePublicOrigin(rawValue: string): string {
    const trimmed = trimTrailingSlashes(rawValue);
    try {
        return new URL(trimmed).origin;
    } catch {
        return trimmed;
    }
}

export function loadAppConfig(env: NodeJS.ProcessEnv = process.env): AppConfig {
    const backendOrigin = env.BACKEND_ORIGIN || env.BACKEND_URL || DEFAULT_BACKEND_ORIGIN;
    const publicOrigin = env.APP_URL || env.FRONTEND_URL || env.PUBLIC_BASE_URL || DEFAULT_PUBLIC_ORIGIN;
    const rawTelegramMiniAppUrl = env.PUBLIC_TELEGRAM_MINI_APP_URL || env.TELEGRAM_MINI_APP_URL || '';

    return {
        backendOrigin: trimTrailingSlashes(backendOrigin),
        publicOrigin: resolvePublicOrigin(publicOrigin),
        telegramMiniAppUrl: rawTelegramMiniAppUrl.trim() ? trimTrailingSlashes(rawTelegramMiniAppUrl.trim()) : null,
        sessionPath: env.SESSION_PATH || DEFAULT_SESSION_PATH,
        wsPath: env.WS_PATH || DEFAULT_WS_PATH,
        devPort: parsePort(env.DEV_PORT, DEFAULT_DEV_PORT),
        previewPort: parsePort(env.PREVIEW_PORT, DEFAULT_PREVIEW_PORT),
    };

}
