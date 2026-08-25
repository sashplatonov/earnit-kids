import { describe, expect, it } from 'vitest';
import { telegramIconMap } from '../../src/lib/components/telegram/telegramIconMap';
import {
    getGraphicsForCategory,
    getSemanticGraphic,
    isSemanticGraphicKey,
    OTHER_GRAPHIC_KEY,
    SEMANTIC_GRAPHIC_CATEGORIES,
    SEMANTIC_GRAPHICS,
} from '../../src/lib/components/telegram/semanticGraphics';

describe('semantic graphics library', () => {
    it('maps every graphic key to a real icon', () => {
        for (const graphic of SEMANTIC_GRAPHICS) {
            expect(telegramIconMap).toHaveProperty(graphic.key);
        }
    });

    it('groups every graphic under a known category', () => {
        const categoryKeys = new Set(SEMANTIC_GRAPHIC_CATEGORIES.map((category) => category.key));
        for (const graphic of SEMANTIC_GRAPHICS) {
            expect(categoryKeys.has(graphic.category)).toBe(true);
        }
    });

    it('contains a deterministic fallback and stable catalog keys', () => {
        expect(isSemanticGraphicKey(OTHER_GRAPHIC_KEY)).toBe(true);
        for (const graphic of SEMANTIC_GRAPHICS) {
            expect(graphic.key).not.toMatch(/\p{Extended_Pictographic}/u);
        }
    });

    it('resolves unknown keys to the fallback graphic', () => {
        const fallback = getSemanticGraphic('does-not-exist');
        expect(fallback.key).toBe(OTHER_GRAPHIC_KEY);
        expect(fallback.category).toBe('general');
    });

    it('resolves known keys exactly', () => {
        expect(getSemanticGraphic('sunrise').key).toBe('sunrise');
        expect(getSemanticGraphic('sunrise').category).toBe('routine');
    });

    it('returns category members in canonical order', () => {
        const general = getGraphicsForCategory('general');
        expect(general.length).toBeGreaterThan(0);
        expect(general[0].key).toBe('circleDot');
    });

    it('rejects prototype-chain keys', () => {
        expect(isSemanticGraphicKey('constructor')).toBe(false);
        expect(isSemanticGraphicKey('toString')).toBe(false);
        expect(isSemanticGraphicKey(null)).toBe(false);
        expect(isSemanticGraphicKey(undefined)).toBe(false);
    });
});
