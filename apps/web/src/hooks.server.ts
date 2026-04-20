import type { Handle } from '@sveltejs/kit';
import { loadAppConfig } from '$lib/server/config';
import { resolveSessionSnapshot } from '$lib/server/session';

const SECURITY_HEADERS = {
    'cross-origin-resource-policy': 'same-site',
    'referrer-policy': 'no-referrer',
    'x-frame-options': 'DENY',
    'x-content-type-options': 'nosniff',
    'x-xss-protection': '1; mode=block',
    'strict-transport-security': 'max-age=31536000; includeSubDomains',
} as const;

export const handle: Handle = async ({ event, resolve }) => {
    event.locals.appConfig = loadAppConfig();
    event.locals.session = await resolveSessionSnapshot(event);

    const response = await resolve(event);

    Object.entries(SECURITY_HEADERS).forEach(([key, value]) => {
        if (!response.headers.has(key)) {
            response.headers.set(key, value);
        }
    });

    return response;
};
