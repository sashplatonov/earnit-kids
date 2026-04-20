import { describe, expect, it } from 'vitest';
import { startOfTodayTimestamp, toDate } from '../../src/lib/utils/date';

describe('toDate', () => {
    it('parses ISO strings into valid Date objects', () => {
        const result = toDate('2026-04-20T10:30:00Z');

        expect(result).toBeInstanceOf(Date);
        expect(result?.toISOString()).toBe('2026-04-20T10:30:00.000Z');
    });

    it('returns null for invalid inputs', () => {
        expect(toDate(123)).toBeNull();
        expect(toDate('not-a-date')).toBeNull();
    });
});

describe('startOfTodayTimestamp', () => {
    it('returns the local midnight timestamp for the given reference time', () => {
        const reference = new Date('2026-04-20T18:45:00Z').getTime();
        const result = startOfTodayTimestamp(reference);
        const midnight = new Date(result);

        expect(midnight.getHours()).toBe(0);
        expect(midnight.getMinutes()).toBe(0);
        expect(midnight.getSeconds()).toBe(0);
        expect(midnight.getMilliseconds()).toBe(0);
    });
});