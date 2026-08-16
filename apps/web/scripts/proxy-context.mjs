function trimTrailingSlashes(value) {
    return value.replace(/\/+$/, '');
}

function resolveDefaultPublicOrigin(env) {
    const host = (env.HOST || 'localhost').trim() || 'localhost';
    const port = (env.PORT || '4174').trim() || '4174';

    return `http://${host}:${port}`;
}

export function resolveProxyContext(env = process.env) {
    const backendOrigin = trimTrailingSlashes(env.BACKEND_ORIGIN || env.BACKEND_URL || 'http://localhost:8080');
    const publicOrigin = trimTrailingSlashes(
        env.APP_URL || env.FRONTEND_URL || env.PUBLIC_BASE_URL || resolveDefaultPublicOrigin(env)
    );

    return {
        backendOrigin,
        backendUrl: new URL(backendOrigin),
        publicOrigin,
        publicUrl: new URL(publicOrigin),
    };
}

export function resolveTelegramMiniAppUrl(env = process.env) {
    // EXPLAIN: The public site button must always be a Telegram deep link
    // EXPLAIN: (https://t.me/<bot>?startapp=<mini-app-url>), never a bare web
    // EXPLAIN: page. The startapp payload is the HTTPS Mini App URL Telegram
    // EXPLAIN: opens inside the bot.
    const botUsername = (env.TELEGRAM_BOT_USERNAME || '').trim();
    if (!botUsername) {
        return '';
    }
    // EXPLAIN: Prefer the explicit Mini App URL (TELEGRAM_MINI_APP_URL); fall
    // EXPLAIN: back to the APP_URL origin + /telegram so the deep link always
    // EXPLAIN: targets the Mini App entry, not the marketing site root.
    const explicit = (env.TELEGRAM_MINI_APP_URL || '').trim();
    const rawOrigin = (env.APP_URL || env.FRONTEND_URL || env.PUBLIC_BASE_URL || '').trim();
    let startapp;
    if (explicit) {
        startapp = explicit.replace(/\/+$/, '');
    } else if (rawOrigin) {
        // EXPLAIN: APP_URL may contain a path in dev/preview; the deep link
        // EXPLAIN: must always point at the site root so Telegram opens a
        // EXPLAIN: valid origin.
        startapp = `${trimTrailingSlashes(new URL(rawOrigin).origin)}/telegram`;
    } else {
        return '';
    }
    return `https://t.me/${botUsername}?startapp=${startapp}`;
}

export function buildProxyReferer(referer, publicOrigin) {
    if (!referer) {
        return null;
    }

    const publicUrl = new URL(publicOrigin);

    try {
        const refererUrl = new URL(referer);
        return `${publicUrl.origin}${refererUrl.pathname}${refererUrl.search}`;
    } catch {
        return publicUrl.origin;
    }
}