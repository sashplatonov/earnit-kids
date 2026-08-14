import { describe, expect, it } from 'vitest';
import {
    getTelegramEntityIcon,
    stripLeadingEmoji,
    type EntityIconInput,
} from '../../src/lib/components/telegram/telegramEntityIcons';

function icon(input: EntityIconInput): string {
    return getTelegramEntityIcon(input);
}

describe('stripLeadingEmoji', () => {
    it('removes leading emoji and decorative glyphs', () => {
        expect(stripLeadingEmoji('🪙 Монетка')).toBe('Монетка');
        expect(stripLeadingEmoji('☀️ Утренний старт')).toBe('Утренний старт');
        expect(stripLeadingEmoji('⭐ Убрать стол')).toBe('Убрать стол');
        expect(stripLeadingEmoji('Простая задача')).toBe('Простая задача');
        expect(stripLeadingEmoji('')).toBe('');
    });
});

describe('getTelegramEntityIcon', () => {
    it('maps morning tasks to sun', () => {
        expect(icon({ kind: 'task', title: 'Умыться и одеться', group: 'Утро' })).toBe('sun');
        expect(icon({ kind: 'task', title: 'Morning routine', group: 'Morning' })).toBe('sun');
    });
    it('maps reading and study to book', () => {
        expect(icon({ kind: 'task', title: 'Книжная искра — 15 минут', group: 'Учёба' })).toBe('book');
        expect(icon({ kind: 'task', title: 'Read for 15 minutes' })).toBe('book');
    });
    it('maps writing to pencil', () => {
        expect(icon({ kind: 'task', title: 'Красивые 5 строк' })).toBe('pencil');
    });
    it('maps desk/cleaning tasks correctly', () => {
        expect(icon({ kind: 'task', title: 'Убрать свой стол или рабочее место', group: 'Дом и порядок' })).toBe('desk');
        expect(icon({ kind: 'task', title: 'Сделать одно дело по дому', group: 'Дом и порядок' })).toBe('home');
    });
    it('maps board games and family time', () => {
        expect(icon({ kind: 'reward', title: 'Выбрать настольную игру на вечер' })).toBe('dice');
        expect(icon({ kind: 'reward', title: 'Поиграть с мамой или папой 20 минут' })).toBe('users');
    });
    it('maps science and gifts', () => {
        expect(icon({ kind: 'reward', title: 'Домашняя лаборатория' })).toBe('flask');
        expect(icon({ kind: 'reward', title: 'Подарок от родителей' })).toBe('gift');
    });
    it('uses deterministic fallbacks', () => {
        expect(icon({ kind: 'task', title: 'Любое непонятное задание' })).toBe('task');
        expect(icon({ kind: 'reward', title: 'Что-то совсем неопределённое' })).toBe('reward');
        expect(icon({ kind: 'category', title: 'Неизвестная группа' })).toBe('tag');
        expect(icon({ kind: 'request', title: 'x' })).toBe('request');
        expect(icon({ kind: 'activity', title: 'x' })).toBe('activity');
        expect(icon({ kind: 'child', title: 'x' })).toBe('child');
    });
    it('honors an explicit semantic override', () => {
        expect(icon({ kind: 'task', title: 'Anything', semantic: 'sun' })).toBe('sun');
    });
    it('ignores prototype-chain keys as semantic overrides', () => {
        expect(icon({ kind: 'task', title: 'x', semantic: 'constructor' })).toBe('task');
        expect(icon({ kind: 'task', title: 'x', semantic: 'toString' })).toBe('task');
        expect(icon({ kind: 'reward', title: 'x', semantic: 'hasOwnProperty' })).toBe('reward');
    });
    it('strips emoji before matching', () => {
        expect(icon({ kind: 'task', title: '☀️ Утренний старт' })).toBe('sun');
    });
});
