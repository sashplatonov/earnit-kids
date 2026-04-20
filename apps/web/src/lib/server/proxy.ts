import type { RequestEvent } from '@sveltejs/kit';
import { loadAppConfig } from '$lib/server/config';

const BODYLESS_METHODS = new Set(['GET', 'HEAD']);
const EXCLUDED_REQUEST_HEADERS = new Set([
    'connection',
    'content-length',
    'forwarded',
    'host',
    'origin',
    'x-forwarded-for',
    'x-forwarded-host',
    'x-forwarded-port',
    'x-forwarded-proto',
]);
const EXCLUDED_RESPONSE_HEADERS = new Set(['connection', 'content-length']);

function cloneHeaders(sourceHeaders: Headers, excludedHeaders: Set<string>): Headers {
    const clonedHeaders = new Headers();

    sourceHeaders.forEach((value, key) => {
        if (!excludedHeaders.has(key.toLowerCase())) {
            clonedHeaders.append(key, value);
        }
    });

    return clonedHeaders;
}

function applyPublicRequestContext(headers: Headers, publicOrigin: string) {
    const publicUrl = new URL(publicOrigin);

    headers.set('origin', publicUrl.origin);
    headers.set('x-forwarded-host', publicUrl.host);
    headers.set('x-forwarded-proto', publicUrl.protocol.replace(/:$/, ''));

    if (publicUrl.port) {
        headers.set('x-forwarded-port', publicUrl.port);
    } else {
        headers.delete('x-forwarded-port');
    }
}

export async function proxyToBackend(event: RequestEvent): Promise<Response> {
    const config = event.locals.appConfig ?? loadAppConfig();
    const targetUrl = new URL(`${event.url.pathname}${event.url.search}`, config.backendOrigin);
    const proxiedHeaders = cloneHeaders(event.request.headers, EXCLUDED_REQUEST_HEADERS);

    applyPublicRequestContext(proxiedHeaders, config.publicOrigin);

    const requestInit: RequestInit & { duplex?: 'half' } = {
        method: event.request.method,
        headers: proxiedHeaders,
        redirect: 'manual',
    };

    if (!BODYLESS_METHODS.has(event.request.method.toUpperCase())) {
        requestInit.body = event.request.body;
        requestInit.duplex = 'half';
    }

    const response = await fetch(targetUrl, requestInit);

    return new Response(response.body, {
        status: response.status,
        statusText: response.statusText,
        headers: cloneHeaders(response.headers, EXCLUDED_RESPONSE_HEADERS),
    });
}
