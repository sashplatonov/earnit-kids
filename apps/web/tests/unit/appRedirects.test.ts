import { describe, expect, it } from 'vitest';

import { actions as rootActions, load as rootLoad } from '../../src/routes/(public)/+page.server';
import { load as loginLoad } from '../../src/routes/login/+page.server';
import { load as appLoad } from '../../src/routes/app/+page.server';
import { LAST_APP_SECTION_COOKIE } from '../../src/lib/app/routes';

type SessionRole = 'super_admin' | 'parent' | 'child';

function cookiesWith(section?: string | null) {
    return {
        get(name: string) {
            if (name !== LAST_APP_SECTION_COOKIE) {
                return undefined;
            }

            return section ?? undefined;
        },
    };
}

function localsWith(role: SessionRole) {
    // EXPLAIN: The public route group resolves to locale `ru`, so redirects
    // EXPLAIN: from the public landing land on the RU app/super-admin shell.
    return {
        locale: 'ru' as const,
        appConfig: {},
        session: {
            authenticated: true,
            role,
        },
    };
}

describe('authenticated app redirects', () => {
    it('returns the current session for unauthenticated login requests', async () => {
        const result = await loginLoad({
            locals: {
                locale: 'en',
                appConfig: {},
                session: {
                    authenticated: false,
                    role: 'child',
                },
            },
            cookies: cookiesWith(null),
        } as never);

        expect(result).toEqual({
            session: {
                authenticated: false,
                role: 'child',
            },
        });
    });

    it('returns a super admin to the saved app section from the root entrypoint', async () => {
        await expect(rootLoad({ locals: localsWith('super_admin'), cookies: cookiesWith('shop') } as never)).rejects.toMatchObject({
            status: 302,
            location: '/ru/app/shop',
        });
    });

    it('keeps the super admin landing on /super-admin when no app state was saved', async () => {
        await expect(rootLoad({ locals: localsWith('super_admin'), cookies: cookiesWith(null) } as never)).rejects.toMatchObject({
            status: 302,
            location: '/ru/super-admin',
        });
    });

    it('uses the saved section on /login and /app redirects', async () => {
        // EXPLAIN: /login is a locale-prefixed auth route, not a bare public
        // EXPLAIN: route, so its locale comes from the path (en here).
        await expect(loginLoad({ locals: { locale: 'en', appConfig: {}, session: { authenticated: true, role: 'super_admin' } }, cookies: cookiesWith('shop') } as never)).rejects.toMatchObject({
            status: 302,
            location: '/en/app/shop',
        });

        await expect(appLoad({ locals: { locale: 'en', appConfig: {}, session: { authenticated: true, role: 'super_admin' } }, cookies: cookiesWith('shop') } as never)).rejects.toMatchObject({
            status: 302,
            location: '/en/app/shop',
        });
    });

    it('redirects accidental root form posts back to a GET route instead of throwing 405', async () => {
        await expect(rootActions.default({
            locals: {
                locale: 'ru',
                appConfig: {},
                session: {
                    authenticated: false,
                    role: 'child',
                },
            },
            cookies: cookiesWith(null),
        } as never)).rejects.toMatchObject({
            status: 303,
            location: '/',
        });
    });
});
