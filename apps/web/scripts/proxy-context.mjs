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