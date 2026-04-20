import { describe, expect, it } from 'vitest';
import { normalizeAnalyticsRecommendations } from '../../src/lib/components/app/sections/analyticsRecommendations';

describe('normalizeAnalyticsRecommendations', () => {
    it('creates unique ids even when recommendation text repeats', () => {
        const recommendations = normalizeAnalyticsRecommendations([
            { icon: '⭐', text: 'Повторить любимое задание' },
            { icon: '⭐', text: 'Повторить любимое задание' },
        ]);

        expect(recommendations).toHaveLength(2);
        expect(recommendations[0].id).not.toBe(recommendations[1].id);
        expect(recommendations[0].text).toBe('Повторить любимое задание');
        expect(recommendations[1].text).toBe('Повторить любимое задание');
    });
});