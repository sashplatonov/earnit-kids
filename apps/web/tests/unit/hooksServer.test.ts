import { afterEach, describe, expect, it, vi } from 'vitest';

import { handleError } from '../../src/hooks.server';

describe('hooks.server handleError', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('logs request context for server-side route failures', () => {
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
        const result = handleError({
            kind: 'unknown',
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
        } as never);

        expect(result).toBeUndefined();
        expect(consoleError).toHaveBeenCalledWith(JSON.stringify({
            severity: 'error',
            eventCode: 'web.server_error',
            route: '/public/index.html',
            status: 500,
            category: 'render',
            traceId: 'trace-123',
            errorClass: 'Error',
        }));
    });

    it('does not log expected unknown-route 404 responses as server errors', () => {
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);

        const result = handleError({
            kind: 'framework',
            error: { status: 404, message: 'Not Found' },
            event: {
                request: {
                    method: 'GET',
                    headers: new Headers(),
                },
                route: { id: null },
                url: new URL('https://example.com/en/wp-login.php'),
            },
        } as never);

        expect(result).toEqual({ status: 404, message: 'Not Found' });
        expect(consoleError).not.toHaveBeenCalled();
    });
});
