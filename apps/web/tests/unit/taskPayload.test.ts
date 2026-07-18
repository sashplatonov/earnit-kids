import { describe, expect, it } from 'vitest';
import { buildTaskPayload } from '../../src/lib/services/taskPayload';

describe('buildTaskPayload', () => {
    it('builds a backend-compatible task payload with both name and title', () => {
        const payload = buildTaskPayload({
            id: 42,
            title: '  Убрать игрушки  ',
            groupName: 'Дом',
            coins: 15,
            comment: 'Перед ужином',
            cueWhen: '  после школы ',
            cueAction: ' убрать игрушки  ',
            freqLimit: '2',
            freqPeriod: 'week',
        });

        expect(payload).toEqual({
            id: 42,
            name: 'Убрать игрушки',
            title: 'Убрать игрушки',
            groupName: 'Дом',
            coins: 15,
            comment: 'Перед ужином',
            cueWhen: 'после школы',
            cueAction: 'убрать игрушки',
            frequency: { limit: 2, period: 'week' },
        });
    });

    it('removes optional fields when they are blank', () => {
        const payload = buildTaskPayload({
            title: 'Полить цветы',
            groupName: '   ',
            coins: 0,
            comment: '   ',
            freqLimit: '',
            freqPeriod: 'day',
        });

        expect(payload).toEqual({
            id: undefined,
            name: 'Полить цветы',
            title: 'Полить цветы',
            groupName: null,
            coins: 10,
            comment: null,
            frequency: null,
        });
    });
});
