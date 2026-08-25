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
                        referer: 'https://example.com/public/index.html',
                        'user-agent': 'Vitest Browser',
                        'x-trace-id': 'trace-123',
                    }),
                },
                route: { id: '/public/index.html' },
                url: new URL('https://example.com/public/index.html'),
            },
            message: 'Server exploded',
            status: 500,
        } as never);

        expect(result).toEqual({ message: 'Server exploded' });
        expect(consoleError).toHaveBeenCalledWith('SvelteKit server error', expect.objectContaining({
            method: 'POST',
            url: 'https://example.com/public/index.html',
            path: '/public/index.html',
            search: '',
            routeId: '/public/index.html',
            status: 500,
            message: 'Server exploded',
            traceId: 'trace-123',
        }));
    });

    it('does not log expected unknown-route 404 responses as server errors', () => {
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);

        const result = handleError({
            error: new Error('Not found: /wp-login.php'),
            event: {
                request: {
                    method: 'GET',
                    headers: new Headers(),
                },
                route: { id: null },
                url: new URL('https://example.com/en/wp-login.php'),
            },
            message: 'Not Found',
            status: 404,
        } as never);

        expect(result).toEqual({ message: 'Not Found' });
        expect(consoleError).not.toHaveBeenCalled();
    });
});
