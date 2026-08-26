import { describe, expect, it } from 'vitest';
import { load } from '../../src/routes/+page.server';

function makeEvent(path = '/', authenticated = false, locale: string | undefined = undefined) {
    return {
        locals: {
            session: { authenticated },
            locale,
        },
        url: new URL(`https://example.test${path}`),
    } as never;
}

describe('root page server load', () => {
    it('keeps the public root for authenticated users', async () => {
        await expect(load(makeEvent('/', true))).resolves.toEqual({});
    });

    it('hands Telegram launch parameters to the Mini App route using the negotiated locale', async () => {
        await expect(load(makeEvent('/?tgWebAppStartParam=pairing-token', true, 'ru'))).rejects.toMatchObject({
            status: 302,
            location: '/ru/telegram?tgWebAppStartParam=pairing-token',
        });
    });

    it('defaults Telegram launch to English when no locale is negotiated', async () => {
        await expect(load(makeEvent('/?tgWebAppStartParam=pairing-token', true))).rejects.toMatchObject({
            status: 302,
            location: '/en/telegram?tgWebAppStartParam=pairing-token',
        });
    });
});
