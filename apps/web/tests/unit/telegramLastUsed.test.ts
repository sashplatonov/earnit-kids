import { describe, expect, it } from 'vitest';
import { formatLastUsedTime } from '../../src/lib/components/telegram/telegramLastUsed';

describe('formatLastUsedTime', () => {
    it('returns empty string for missing/invalid values', () => {
        expect(formatLastUsedTime(null, 'ru')).toBe('');
        expect(formatLastUsedTime(undefined, 'ru')).toBe('');
        expect(formatLastUsedTime('not-a-date', 'ru')).toBe('');
    });

    it('labels today with a localized relative prefix', () => {
        const now = new Date();
        now.setHours(8, 32, 0, 0);
        const iso = now.toISOString();
        expect(formatLastUsedTime(iso, 'ru')).toMatch(/^сегодня, /);
        expect(formatLastUsedTime(iso, 'en')).toMatch(/^today, /);
    });

    it('renders a short date and time for older timestamps', () => {
        const date = new Date(Date.UTC(2026, 7, 12, 19, 40, 0));
        const iso = date.toISOString();
        const result = formatLastUsedTime(iso, 'ru');
        expect(result).toContain('авг.');
        expect(result).toMatch(/\d{2}:\d{2}$/);
    });
});
