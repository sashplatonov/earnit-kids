import { describe, expect, it } from 'vitest';

import {
    buildI18nPayload,
    getI18nPayloadForPath,
    translateKey,
    validateCatalogs,
} from '../../../src/lib/i18n';

describe('translation catalogs', () => {
    it('keeps Russian keys and interpolation placeholders in parity with English', async () => {
        expect(await validateCatalogs()).toEqual([]);
    });

    it('loads only the domains required by a route', async () => {
        const payload = await getI18nPayloadForPath('/en/select-family', 'en');

        expect(payload.domains).toEqual(['common', 'public', 'auth', 'errors']);
        expect(payload.messages.tasks).toBeUndefined();
        expect(payload.messages.app).toBeUndefined();
    });

    it('falls back to English without exposing a missing localized key', async () => {
        const payload = await buildI18nPayload('ru', ['common']);
        const message = translateKey(payload, 'common.locale.label');

        expect(message).toBe('Язык');
        expect(translateKey(payload, 'common.locale.select.en')).toBe('Переключить на английский');
    });

    it('returns an empty production-safe value for an unknown runtime key', async () => {
        const payload = await buildI18nPayload('en', ['common']);

        expect(() => translateKey(payload, 'common.unknown' as never)).toThrow('Missing English translation');
    });
});
