import type { RequestEvent } from '@sveltejs/kit';
import { DEFAULT_LOCALE } from '$lib/i18n';
import type { SessionSnapshot } from '$lib/types/session';
import { loadAppConfig } from '$lib/server/config';
import { emitDiagnostic, requestTraceId, safeErrorClass } from './diagnostics';

const GUEST_SESSION: SessionSnapshot = { authenticated: false };

function buildForwardedHeaders(event: RequestEvent): Headers {
    const headers = new Headers();
    const requestHeaders = event.request.headers;
    const locale = event.locals.locale ?? DEFAULT_LOCALE;

    headers.set('accept', 'application/json');
    headers.set('accept-language', locale);
    headers.set('cookie', requestHeaders.get('cookie') ?? '');
    headers.set('user-agent', requestHeaders.get('user-agent') ?? 'apps-web');
    headers.set('x-app-locale', locale);
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
    const traceId = requestTraceId(event.request);

    try {
        const response = await fetch(`${config.backendOrigin}${config.sessionPath}`, {
            method: 'GET',
            headers: buildForwardedHeaders(event),
            signal: controller.signal,
        });

        if (!response.ok) {
            emitDiagnostic({ severity: 'warn', code: 'web.session_failure', route: config.sessionPath, status: response.status, category: 'upstream_status', traceId });
            return GUEST_SESSION;
        }

        const session = (await response.json()) as SessionSnapshot;
        return session;
    } catch (e) {
        emitDiagnostic({ severity: 'warn', code: 'web.session_failure', route: config.sessionPath, category: 'upstream_unavailable', traceId, errorClass: safeErrorClass(e) });
        return GUEST_SESSION;
    } finally {
        clearTimeout(timeoutId);
    }
}
