import { describe, expect, it } from 'vitest';
import { appMessages as enAppMessages } from '../../src/lib/i18n/messages/en/app';
import { appMessages as ruAppMessages } from '../../src/lib/i18n/messages/ru/app';

function collectLeafKeys(tree: unknown, prefix = ''): string[] {
    if (tree === null || typeof tree !== 'object' || Array.isArray(tree)) {
        return prefix ? [prefix] : [];
    }
    const keys: string[] = [];
    for (const [key, value] of Object.entries(tree as Record<string, unknown>)) {
        const path = prefix ? `${prefix}.${key}` : key;
        keys.push(...collectLeafKeys(value, path));
    }
    return keys;
}

describe('i18n en↔ru structural parity', () => {
    it('keeps en and ru app catalogs leaf-key aligned', () => {
        const englishKeys = collectLeafKeys(enAppMessages);
        const russianKeys = collectLeafKeys(ruAppMessages);
        expect([...russianKeys].sort()).toEqual([...englishKeys].sort());
    });

    it('keeps the en and ru telegram subtrees leaf-key aligned', () => {
        const enTelegram = (enAppMessages as Record<string, unknown>).telegram;
        const ruTelegram = (ruAppMessages as Record<string, unknown>).telegram;
        expect(enTelegram).toBeDefined();
        expect(ruTelegram).toBeDefined();
        expect(collectLeafKeys(ruTelegram).sort()).toEqual(collectLeafKeys(enTelegram).sort());
    });

    it('translates the core telegram UI strings into Russian', () => {
        const ruTelegram = (ruAppMessages as Record<string, unknown>).telegram as Record<string, unknown>;
        const family = ruTelegram.family as Record<string, unknown>;
        const groupSubnav = ruTelegram.groupSubnav as Record<string, unknown>;
        const importLabels = ruTelegram.import as Record<string, unknown>;
        const limits = ruTelegram.limits as Record<string, unknown>;
        expect(family.familySettings).toBe('Настройки семьи');
        expect(groupSubnav.all).toBe('Все');
        expect(groupSubnav.more).toBe('Ещё');
        expect(importLabels.title).toBe('Импорт из CSV');
        expect(limits.title).toBe('Лимиты');
    });
});
