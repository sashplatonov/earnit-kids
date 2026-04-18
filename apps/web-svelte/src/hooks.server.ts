import type { Handle } from '@sveltejs/kit';
import { loadAppConfig } from '$lib/server/config';
import { resolveSessionSnapshot } from '$lib/server/session';

export const handle: Handle = async ({ event, resolve }) => {
    event.locals.appConfig = loadAppConfig();
    event.locals.session = await resolveSessionSnapshot(event);

    return resolve(event);
};
