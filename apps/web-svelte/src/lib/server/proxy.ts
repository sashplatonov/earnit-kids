import type { RequestEvent } from '@sveltejs/kit';
import { loadAppConfig } from '$lib/server/config';

const BODYLESS_METHODS = new Set(['GET', 'HEAD']);
const EXCLUDED_REQUEST_HEADERS = new Set(['connection', 'content-length', 'host']);
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

export async function proxyToBackend(event: RequestEvent): Promise<Response> {
    const config = event.locals.appConfig ?? loadAppConfig();
    const targetUrl = new URL(`${event.url.pathname}${event.url.search}`, config.backendOrigin);
    const requestInit: RequestInit & { duplex?: 'half' } = {
        method: event.request.method,
        headers: cloneHeaders(event.request.headers, EXCLUDED_REQUEST_HEADERS),
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
