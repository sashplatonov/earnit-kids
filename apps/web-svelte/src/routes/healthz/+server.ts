import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';

export const GET: RequestHandler = ({ locals }) => {
    return json({
        status: 'ok',
        service: 'web-svelte',
        backendOrigin: locals.appConfig.backendOrigin,
        sessionPath: locals.appConfig.sessionPath,
        wsPath: locals.appConfig.wsPath,
    });
};
