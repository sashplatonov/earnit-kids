import { describe, expect, it } from 'vitest';
import {
    MAX_CHILD_LIMIT,
    clampLimit,
    stepLimit,
    effectiveLimit,
} from '../../src/lib/components/telegram/telegramLimits';

describe('telegramLimits', () => {
    it('clamps values into the valid [0, max] range', () => {
        expect(clampLimit(-5)).toBe(0);
        expect(clampLimit(0)).toBe(0);
        expect(clampLimit(15)).toBe(15);
        expect(clampLimit(MAX_CHILD_LIMIT + 100)).toBe(MAX_CHILD_LIMIT);
    });

    it('applies ±1 and ±5 steps without going negative', () => {
        expect(stepLimit(15, 1)).toBe(16);
        expect(stepLimit(15, 5)).toBe(20);
        expect(stepLimit(15, -1)).toBe(14);
        expect(stepLimit(15, -5)).toBe(10);
        expect(stepLimit(0, -5)).toBe(0);
        expect(stepLimit(MAX_CHILD_LIMIT, 5)).toBe(MAX_CHILD_LIMIT);
    });

    it('derives 0 when the toggle is off, else the clamped maximum', () => {
        expect(effectiveLimit(false, 15)).toBe(0);
        expect(effectiveLimit(true, 15)).toBe(15);
        expect(effectiveLimit(true, -3)).toBe(0);
        expect(effectiveLimit(true, MAX_CHILD_LIMIT + 50)).toBe(MAX_CHILD_LIMIT);
    });
});
