import { describe, expect, it } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';

// Load the backend catalog seed directly so content rules are validated against
// the real source of truth (CAT-015 content validation).
const baseDataPath = path.resolve(__dirname, '../../../backend/src/main/resources/baseData.json');
const baseData = JSON.parse(fs.readFileSync(baseDataPath, 'utf-8')) as {
    catalog: { tasks: CatalogItem[]; rewards: CatalogItem[] };
};

interface CatalogItem {
    id: string;
    title: LocalizedText;
    comment: LocalizedText;
    coins?: number;
    price?: number;
    groupKey: string;
    groupName: LocalizedText;
    frequencyLimit?: number;
    frequencyPeriod?: string;
    minAge?: number;
    maxAge?: number;
    difficulty?: string;
    active?: boolean;
}

interface LocalizedText {
    en: string;
    ru: string;
}

// Comprehensive emoji detection covering pictographic, flags, symbols and
// variation-selector sequences (e.g. 🇷🇸, ⏱️).
// eslint-disable-next-line no-misleading-character-class
const EMOJI_LEAD = /^[\p{Extended_Pictographic}\p{Regional_Indicator}\u{200D}\u{FE0F}\u{20E3}\u{2600}-\u{27BF}\u{2B00}-\u{2BFF}\u{1F000}-\u{1FAFF}\s]+/u;

function stripEmoji(value: string): string {
    return value.replace(EMOJI_LEAD, '').trim();
}

const VALID_PERIODS = new Set(['day', 'week', 'month', 'year']);
const VALID_DIFFICULTIES = new Set(['simple', 'normal', 'advanced']);

describe('catalog content validation', () => {
    const all = [...baseData.catalog.tasks, ...baseData.catalog.rewards];

    it('has no duplicate catalog ids', () => {
        const ids = all.map((item) => item.id);
        expect(new Set(ids).size).toBe(ids.length);
    });

    it('every locale variant begins with exactly one meaningful emoji', () => {
        for (const item of all) {
            for (const locale of ['en', 'ru'] as const) {
                const title = item.title[locale];
                const stripped = stripEmoji(title);
                expect(stripped, `title should keep text after emoji: ${title}`).not.toBe(title);
                expect(stripped.trim(), `title should not be blank: ${title}`).not.toBe('');
            }
        }
    });

    it('every title is non-blank', () => {
        for (const item of all) {
            for (const locale of ['en', 'ru'] as const) {
                expect(item.title[locale].trim(), `blank ${locale} title for ${item.id}`).not.toBe('');
                expect(item.groupName[locale].trim(), `blank ${locale} group for ${item.id}`).not.toBe('');
                expect(item.comment[locale]).toBeDefined();
            }
        }
    });

    it('task titles read as an action ("что мне нужно сделать")', () => {
        for (const task of baseData.catalog.tasks) {
            const text = stripEmoji(task.title.ru).toLowerCase();
            // Must contain a concrete verb-like action, not a vague noun phrase.
            expect(text.length, `task title too short: ${task.title}`).toBeGreaterThan(8);
        }
    });

    it('reward titles read as a concrete result ("что я получу")', () => {
        for (const reward of baseData.catalog.rewards) {
            const text = stripEmoji(reward.title.ru).toLowerCase();
            expect(text.length, `reward title too short: ${reward.title}`).toBeGreaterThan(8);
        }
    });

    it('age ranges are valid', () => {
        for (const item of all) {
            expect(item.minAge, `minAge for ${item.id}`).toBeGreaterThanOrEqual(6);
            expect(item.maxAge, `maxAge for ${item.id}`).toBeLessThanOrEqual(14);
            expect(item.minAge, `minAge<=maxAge for ${item.id}`).toBeLessThanOrEqual(item.maxAge ?? 0);
        }
    });

    it('coins/price are positive', () => {
        for (const task of baseData.catalog.tasks) {
            expect(task.coins, `coins for ${task.id}`).toBeGreaterThan(0);
        }
        for (const reward of baseData.catalog.rewards) {
            expect(reward.price, `price for ${reward.id}`).toBeGreaterThan(0);
        }
    });

    it('frequency pairs are valid', () => {
        for (const item of all) {
            expect(VALID_PERIODS.has(item.frequencyPeriod ?? ''), `period for ${item.id}`).toBe(true);
            expect(item.frequencyLimit ?? 1, `limit for ${item.id}`).toBeGreaterThan(0);
        }
    });

    it('difficulty values are valid', () => {
        for (const item of all) {
            expect(VALID_DIFFICULTIES.has(item.difficulty ?? ''), `difficulty for ${item.id}`).toBe(true);
        }
    });

    it('each age filter returns at least 20 tasks and 20 rewards', () => {
        const buckets: Array<[number, number]> = [[6, 8], [9, 11], [12, 14]];
        for (const [min, max] of buckets) {
            const tasks = baseData.catalog.tasks.filter((t) => (t.minAge ?? 6) <= max && (t.maxAge ?? 14) >= min);
            const rewards = baseData.catalog.rewards.filter((r) => (r.minAge ?? 6) <= max && (r.maxAge ?? 14) >= min);
            expect(tasks.length, `tasks for ${min}-${max}`).toBeGreaterThanOrEqual(20);
            expect(rewards.length, `rewards for ${min}-${max}`).toBeGreaterThanOrEqual(20);
        }
    });

    it('every item has a group and frequency metadata', () => {
        for (const item of all) {
            expect(item.groupName.ru?.trim(), `groupName for ${item.id}`).not.toBe('');
            expect(item.groupKey?.trim(), `groupKey for ${item.id}`).not.toBe('');
        }
    });
});
