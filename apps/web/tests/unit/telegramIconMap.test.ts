import { describe, expect, it } from 'vitest';
import { telegramIconMap } from '../../src/lib/components/telegram/telegramIconMap';

describe('telegram icon vocabulary', () => {
    it('covers every Telegram action family', () => {
        expect(Object.keys(telegramIconMap)).toEqual(expect.arrayContaining([
            'approve', 'reject', 'done', 'requestReward', 'coinAdjustment',
            'childSwitch', 'openApp', 'back', 'filter', 'add', 'edit', 'archive',
            'delete', 'task', 'reward', 'request', 'activity', 'family', 'balance',
            'history', 'child', 'remove', 'home'
        ]));
    });
});
