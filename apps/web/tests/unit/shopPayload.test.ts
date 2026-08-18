import { describe, expect, it } from 'vitest';
import { buildShopPayload } from '../../src/lib/telegram/services/shopPayload';

describe('buildShopPayload', () => {
    it('builds a backend-compatible shop payload with frequency and money limits', () => {
        const payload = buildShopPayload({
            id: 7,
            title: '  Час планшета  ',
            groupName: 'Развлечения',
            price: 80,
            comment: 'После уроков',
            freqLimit: '2',
            freqPeriod: 'week',
            moneyLimit: '1500',
            itemType: 'large',
        });

        expect(payload).toEqual({
            id: 7,
            name: 'Час планшета',
            title: 'Час планшета',
            groupName: 'Развлечения',
            price: 80,
            coins: 80,
            comment: 'После уроков',
            frequency: { limit: 2, period: 'week' },
            moneyLimit: 1500,
            type: 'large',
            itemType: 'large',
            icon: null,
        });
    });

    it('keeps the semantic icon key when provided', () => {
        const payload = buildShopPayload({
            id: 8,
            title: 'Настольная игра',
            groupName: 'Семья',
            price: 30,
            comment: '',
            freqLimit: '',
            freqPeriod: 'week',
            moneyLimit: '',
            itemType: 'micro',
            icon: 'dice',
        });
        expect(payload.icon).toBe('dice');
    });

    it('removes optional shop limits when they are blank', () => {
        const payload = buildShopPayload({
            title: 'Наклейки',
            groupName: '   ',
            price: 0,
            comment: '   ',
            freqLimit: '',
            freqPeriod: 'day',
            moneyLimit: '',
            itemType: 'small',
        });

        expect(payload).toEqual({
            id: undefined,
            name: 'Наклейки',
            title: 'Наклейки',
            groupName: null,
            price: 50,
            coins: 50,
            comment: null,
            frequency: null,
            moneyLimit: null,
            type: 'small',
            itemType: 'small',
            icon: null,
        });
    });
});
