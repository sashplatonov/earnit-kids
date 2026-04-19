import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';

export const GET: RequestHandler = ({ locals }) => {
    return json({
        status: 'ok',
        service: 'web',
        backendUrl: locals.appConfig.backendOrigin,
    });
};
