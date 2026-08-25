import { describe, expect, it } from 'vitest';
import { load } from '../../src/routes/+page.server';

function makeEvent(path = '/', authenticated = false) {
    return {
        locals: {
            session: { authenticated },
        },
        url: new URL(`https://example.test${path}`),
    } as never;
}

describe('root page server load', () => {
    it('keeps the public root for authenticated users', async () => {
        await expect(load(makeEvent('/', true))).rejects.toMatchObject({
            status: 302,
            location: '/public/index.html',
        });
    });

    it('hands Telegram launch parameters to the Russian Mini App route', async () => {
        await expect(load(makeEvent('/?tgWebAppStartParam=pairing-token', true))).rejects.toMatchObject({
            status: 302,
            location: '/ru/telegram?tgWebAppStartParam=pairing-token',
        });
    });
});
