import { describe, expect, it } from 'vitest';
import { formatLabelSuffix } from '$lib/utils/labelSuffix';

describe('formatLabelSuffix', () => {
    it('adds colon when label has no colon', () => {
        expect(formatLabelSuffix('Note', 'hello')).toBe('Note: hello');
    });

    it('does not duplicate colon when label already ends with colon', () => {
        expect(formatLabelSuffix('Note:', 'hello')).toBe('Note: hello');
    });

    it('trims whitespace', () => {
        expect(formatLabelSuffix('  Note:  ', '  hello  ')).toBe('Note: hello');
    });

    it('returns empty string when label or value is blank', () => {
        expect(formatLabelSuffix('', 'hello')).toBe('');
        expect(formatLabelSuffix('Note:', '')).toBe('');
        expect(formatLabelSuffix('   ', 'hello')).toBe('');
        expect(formatLabelSuffix('Note:', '   ')).toBe('');
    });
});
