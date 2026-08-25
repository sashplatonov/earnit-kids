import type { RequestEvent } from '@sveltejs/kit';
import { DEFAULT_LOCALE } from '$lib/i18n';
import { loadAppConfig } from '$lib/server/config';
import { emitDiagnostic, requestTraceId, safeErrorClass } from './diagnostics';

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
    const locale = event.locals.locale ?? DEFAULT_LOCALE;
    const traceId = requestTraceId(event.request);

    applyPublicRequestContext(proxiedHeaders, config.publicOrigin);
    proxiedHeaders.set('accept-language', locale);
    proxiedHeaders.set('x-app-locale', locale);
    proxiedHeaders.set('x-trace-id', traceId);

    const requestInit: RequestInit & { duplex?: 'half' } = {
        method: event.request.method,
        headers: proxiedHeaders,
        redirect: 'manual',
    };

    if (!BODYLESS_METHODS.has(event.request.method.toUpperCase())) {
        requestInit.body = event.request.body;
        requestInit.duplex = 'half';
    }

    let response: Response;
    try {
        response = await fetch(targetUrl, requestInit);
    } catch (error) {
        emitDiagnostic({
            severity: 'error',
            code: 'web.proxy_failure',
            route: event.url.pathname,
            category: 'upstream_unavailable',
            traceId,
            errorClass: safeErrorClass(error),
        });
        throw error;
    }

    return new Response(response.body, {
        status: response.status,
        statusText: response.statusText,
        headers: (() => {
            const headers = cloneHeaders(response.headers, EXCLUDED_RESPONSE_HEADERS);
            headers.set('x-trace-id', response.headers.get('x-trace-id') ?? traceId);
            return headers;
        })(),
    });
}
