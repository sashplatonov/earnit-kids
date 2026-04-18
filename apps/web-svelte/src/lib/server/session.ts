import type { RequestEvent } from '@sveltejs/kit';
import type { SessionSnapshot } from '$lib/types/session';
import { loadAppConfig } from '$lib/server/config';

const GUEST_SESSION: SessionSnapshot = { authenticated: false };

function buildForwardedHeaders(event: RequestEvent): Headers {
    const headers = new Headers();
    const requestHeaders = event.request.headers;

    headers.set('accept', 'application/json');
    headers.set('cookie', requestHeaders.get('cookie') ?? '');
    headers.set('user-agent', requestHeaders.get('user-agent') ?? 'apps-web-svelte');
    headers.set('x-forwarded-host', requestHeaders.get('x-forwarded-host') ?? event.url.host);
    headers.set('x-forwarded-proto', requestHeaders.get('x-forwarded-proto') ?? event.url.protocol.replace(':', ''));

    const forwardedFor = requestHeaders.get('x-forwarded-for');
    if (forwardedFor) {
        headers.set('x-forwarded-for', forwardedFor);
    }

    return headers;
}

export async function resolveSessionSnapshot(event: RequestEvent): Promise<SessionSnapshot> {
    const config = event.locals.appConfig ?? loadAppConfig();
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 3000);

    try {
        const response = await fetch(`${config.backendOrigin}${config.sessionPath}`, {
            method: 'GET',
            headers: buildForwardedHeaders(event),
            signal: controller.signal,
        });

        if (!response.ok) {
            return GUEST_SESSION;
        }

        return (await response.json()) as SessionSnapshot;
    } catch {
        return GUEST_SESSION;
    } finally {
        clearTimeout(timeoutId);
    }
}
