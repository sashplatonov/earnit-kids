import { describe, expect, it, vi } from 'vitest';

import { updateFamilyLocale } from '../../src/lib/services/api';
import { commonMessages as enCommon } from '../../src/lib/i18n/messages/en/common';
import { commonMessages as ruCommon } from '../../src/lib/i18n/messages/ru/common';

describe('family locale switcher update', () => {
    it('returns a successful typed result for a family locale update', async () => {
        const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 204 }));
        vi.stubGlobal('fetch', fetchMock);

        await expect(updateFamilyLocale('ru')).resolves.toEqual({ ok: true, data: null });
        expect(fetchMock).toHaveBeenCalledWith('/api/family/locale', expect.objectContaining({
            credentials: 'same-origin',
            method: 'PUT',
            body: JSON.stringify({ locale: 'ru' }),
        }));

        vi.unstubAllGlobals();
    });

    it('preserves structured HTTP and network failures for retry presentation', async () => {
        const rejectedResponse = vi.fn<typeof fetch>().mockResolvedValue(new Response(
            JSON.stringify({ errorCode: 'UNSUPPORTED_LOCALE' }),
            { status: 400, headers: { 'Content-Type': 'application/json' } },
        ));
        vi.stubGlobal('fetch', rejectedResponse);

        await expect(updateFamilyLocale('english')).resolves.toMatchObject({
            ok: false,
            errorCode: 'UNSUPPORTED_LOCALE',
            status: 400,
        });

        vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockRejectedValue(new Error('offline')));
        await expect(updateFamilyLocale('ru')).resolves.toMatchObject({
            ok: false,
            errorCode: null,
            status: 0,
        });

        expect(enCommon.locale.updateFailed).toBe('Could not update the family language.');
        expect(enCommon.locale.retry).toBe('Try again');
        expect(ruCommon.locale.updateFailed).toBe('Не удалось изменить язык семьи.');
        expect(ruCommon.locale.retry).toBe('Повторить');
        vi.unstubAllGlobals();
    });
});
