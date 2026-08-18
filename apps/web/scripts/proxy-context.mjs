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
    // EXPLAIN: (https://t.me/<bot>?startapp=<command>), never a bare web page.
    // EXPLAIN: The Mini App HTTPS URL itself is configured in BotFather
    // EXPLAIN: (Bot Settings → Configure Mini App), NOT passed in startapp.
    // EXPLAIN: Telegram rejects URL-shaped startapp values (containing "://")
    // EXPLAIN: with START_PARAM_INVALID; startapp must be a short command
    // EXPLAIN: token (letters, digits, _, -), as the backend already does for
    // EXPLAIN: pairing invites (pi_/ci_ prefixes). The /telegram route ignores
    // EXPLAIN: non-token start_param values, so "home" is a safe default.
    const botUsername = (env.TELEGRAM_BOT_USERNAME || '').trim();
    if (!botUsername) {
        return '';
    }
    const explicit = (env.TELEGRAM_MINI_APP_URL || '').trim();
    // EXPLAIN: If TELEGRAM_MINI_APP_URL is already a t.me deep link, use it
    // EXPLAIN: verbatim so operators can override the startapp command.
    if (explicit.startsWith('https://t.me/') || explicit.startsWith('http://t.me/')) {
        return explicit.replace(/\/+$/, '');
    }
    // EXPLAIN: Otherwise build the deep link with a short startapp command.
    // EXPLAIN: "home" is a neutral command the /telegram route treats as a
    // EXPLAIN: non-pairing value (not hex, not pi_/ci_), so the user lands in
    // EXPLAIN: the Mini App and logs in normally.
    return `https://t.me/${botUsername}?startapp=home`;
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