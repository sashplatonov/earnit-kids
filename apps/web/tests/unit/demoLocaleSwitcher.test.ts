import { describe, expect, it } from 'vitest';
import { stripLocaleFromPath, swapPathLocale } from '../../src/lib/i18n/config';

describe('demo locale switcher contract', () => {
    it('keeps demo route navigation locale-specific', () => {
        expect(swapPathLocale('/demo', 'ru')).toBe('/ru/demo');
        expect(stripLocaleFromPath(swapPathLocale('/ru/demo', 'en'))).toBe('/demo');
    });

    it('does not turn the demo path into a family-managed locale endpoint', () => {
        expect(swapPathLocale('/demo', 'ru')).toBe('/ru/demo');
    });
});
