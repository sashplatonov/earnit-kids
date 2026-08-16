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
    // EXPLAIN: Prefer an explicit full Mini App URL when provided; otherwise
    // EXPLAIN: form the Telegram deep link automatically from the bot username
    // EXPLAIN: and the public site origin (APP_URL) so the user lands in the
    // EXPLAIN: Mini App without a hand-maintained hosting URL.
    const explicit = (env.TELEGRAM_MINI_APP_URL || '').trim();
    if (explicit) {
        return explicit.replace(/\/+$/, '');
    }
    const botUsername = (env.TELEGRAM_BOT_USERNAME || '').trim();
    const rawOrigin = (env.APP_URL || env.FRONTEND_URL || env.PUBLIC_BASE_URL || '').trim();
    if (!botUsername || !rawOrigin) {
        return '';
    }
    // EXPLAIN: APP_URL may contain a path in dev/preview; the deep link must
    // EXPLAIN: always point at the site root so Telegram opens a valid origin.
    const publicOrigin = trimTrailingSlashes(new URL(rawOrigin).origin);
    // EXPLAIN: The startapp payload opens the Mini App inside Telegram. It must
    // EXPLAIN: target the /telegram entry, not the public marketing site root,
    // EXPLAIN: so the bot button lands on the actual Mini App surface.
    return `https://t.me/${botUsername}?startapp=${publicOrigin}/telegram`;
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