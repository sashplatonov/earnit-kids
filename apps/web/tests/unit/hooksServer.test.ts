import { afterEach, describe, expect, it, vi } from 'vitest';

import { handleError } from '../../src/hooks.server';

describe('hooks.server handleError', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('logs request context for server-side route failures', () => {
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
        const result = handleError({
            error: new Error('Boom'),
            event: {
                request: {
                    method: 'POST',
                    headers: new Headers({
                        referer: 'https://example.com/login',
                        'user-agent': 'Vitest Browser',
                        'x-trace-id': 'trace-123',
                    }),
                },
                route: { id: '/login' },
                url: new URL('https://example.com/en/login?mode=register'),
            },
            message: 'Server exploded',
            status: 500,
        } as never);

        expect(result).toEqual({ message: 'Server exploded' });
        expect(consoleError).toHaveBeenCalledWith('SvelteKit server error', expect.objectContaining({
            method: 'POST',
            url: 'https://example.com/en/login?mode=register',
            path: '/en/login',
            search: '?mode=register',
            routeId: '/login',
            status: 500,
            message: 'Server exploded',
            traceId: 'trace-123',
        }));
    });
});
