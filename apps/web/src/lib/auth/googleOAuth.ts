type GoogleLoginUrlPayload = {
    error?: unknown;
    message?: unknown;
    url?: unknown;
};

export const GOOGLE_LOGIN_NETWORK_ERROR = 'GOOGLE_LOGIN_NETWORK_ERROR';
export const GOOGLE_LOGIN_URL_UNAVAILABLE = 'GOOGLE_LOGIN_URL_UNAVAILABLE';

export async function requestGoogleLoginUrl(fetchImpl: typeof fetch, redirectTo: string): Promise<string> {
    let response: Response;

    try {
        response = await fetchImpl(`/api/login-google/url?redirect_to=${encodeURIComponent(redirectTo)}`, {
            credentials: 'same-origin',
            cache: 'no-store',
        });
    } catch {
        throw new Error(GOOGLE_LOGIN_NETWORK_ERROR);
    }

    const body = (await response.json().catch(() => ({}))) as GoogleLoginUrlPayload;

    if (response.ok && typeof body.url === 'string' && body.url) {
        return body.url;
    }

    if (typeof body.error === 'string' && body.error) {
        throw new Error(body.error);
    }

    if (typeof body.message === 'string' && body.message) {
        throw new Error(body.message);
    }

    throw new Error(GOOGLE_LOGIN_URL_UNAVAILABLE);
}