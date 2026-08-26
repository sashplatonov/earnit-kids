import { describe, expect, it } from 'vitest';
import { load as loadApp } from '../../src/routes/app/+page.server';
import { load as loadWorkspace } from '../../src/routes/workspace/+page.server';

function makeEvent(path: string, authenticated: boolean) {
    return {
        locals: {
            locale: path.startsWith('/ru/') ? 'ru' : 'en',
            session: {
                authenticated,
                role: 'parent',
            },
            appConfig: { publicOrigin: 'https://example.test' },
        },
        url: new URL(`https://example.test${path}`),
    } as never;
}

async function captureRedirect(operation: () => unknown) {
    try {
        await operation();
    } catch (error) {
        return error as { status: number; location: string };
    }

    throw new Error('Expected a redirect');
}

describe('browser application routes', () => {
    it.each([
        ['/workspace?tab=tasks', '/app?tab=tasks'],
        ['/ru/workspace?tab=tasks', '/ru/app?tab=tasks'],
    ])('redirects legacy route %s to %s', async (path, location) => {
        const redirect = await captureRedirect(() => loadWorkspace(makeEvent(path, true)));
        expect(redirect.status).toBe(308);
        expect(redirect.location).toBe(location);
    });

    it('keeps an unauthenticated app continuation localized', async () => {
        const redirect = await captureRedirect(() => loadApp(makeEvent('/ru/app?tab=tasks', false)));
        expect(redirect.status).toBe(302);
        expect(redirect.location).toBe('/?continue=%2Fru%2Fapp%3Ftab%3Dtasks');
    });

    it('returns the existing app bootstrap data for authenticated users', async () => {
        await expect(loadApp(makeEvent('/app', true))).resolves.toEqual({
            role: 'parent',
            publicOrigin: 'https://example.test',
        });
    });
});
