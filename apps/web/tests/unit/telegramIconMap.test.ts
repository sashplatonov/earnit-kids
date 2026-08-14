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

    it('covers every canonical semantic graphic', () => {
        expect(Object.keys(telegramIconMap)).toEqual(expect.arrayContaining([
            'sunrise', 'moon', 'bed', 'shower', 'shirt', 'utensils', 'droplet',
            'pencilLine', 'calculator', 'school', 'languages', 'music', 'table',
            'sprout', 'paw', 'dumbbell', 'bike', 'footprints', 'film', 'gamepad',
            'palette', 'penTool', 'blocks', 'treePine', 'trees', 'iceCream',
            'cake', 'car', 'piggy', 'medal', 'award', 'trophy', 'star', 'target',
            'calendar', 'clock', 'volleyball', 'brush', 'cookingPot', 'bath',
            'circleDot', 'questionMark', 'upload', 'mail', 'gauge', 'pause',
            'play', 'send', 'key', 'eye', 'copy', 'file', 'unlink'
        ]));
    });
});
