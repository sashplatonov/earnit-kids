import { describe, expect, it } from 'vitest';
import {
    EMPTY_FILTERS,
    catalogGroups,
    filterCatalog,
    formatFrequency,
    isAlreadyAdded,
    mapGroupKeyToFamily,
    matchesAge,
    matchesDifficulty,
    matchesFrequency,
    matchesPurchase,
    matchesSearch,
    nonAgeFilterCount,
    stripEmoji,
    templateToReward,
    templateToTask,
} from '../../src/lib/services/catalogFilter';
import type { CatalogRewardTemplate, CatalogTaskTemplate } from '../../src/lib/stores/app';

const taskTemplate: CatalogTaskTemplate = {
    id: 'ct-6-8-1',
    title: '🌅 Умыться, одеться и причесаться',
    coins: 1,
    groupKey: 'morning',
    groupName: 'Утро и вечер',
    semanticGraphicKey: 'sunrise',
    frequencyLimit: 1,
    frequencyPeriod: 'day',
    minAge: 6,
    maxAge: 8,
    difficulty: 'simple',
    tags: ['morning'],
    active: true,
    sortOrder: 1,
};

const rewardTemplate: CatalogRewardTemplate = {
    id: 'cr-6-8-1',
    title: '🎲 Выбрать настольную игру на вечер',
    price: 4,
    groupKey: 'family',
    groupName: 'Время с семьёй',
    semanticGraphicKey: 'users',
    frequencyLimit: 1,
    frequencyPeriod: 'day',
    minAge: 6,
    maxAge: 8,
    difficulty: 'simple',
    tags: ['family'],
    active: true,
    sortOrder: 1,
};

describe('stripEmoji', () => {
    it('removes a single leading emoji and keeps the text', () => {
        expect(stripEmoji('🌅 Умыться')).toBe('Умыться');
        expect(stripEmoji('📖 Почитать книгу')).toBe('Почитать книгу');
    });
    it('leaves plain text unchanged', () => {
        expect(stripEmoji('Умыться')).toBe('Умыться');
    });
});

describe('matchesAge', () => {
    it('returns true when no age filter is set', () => {
        expect(matchesAge(taskTemplate, null)).toBe(true);
    });
    it('matches an overlapping age bucket', () => {
        expect(matchesAge(taskTemplate, '6-8')).toBe(true);
        expect(matchesAge(taskTemplate, '9-11')).toBe(false);
    });
});

describe('matchesDifficulty', () => {
    it('matches exact difficulty and ignores null filter', () => {
        expect(matchesDifficulty(taskTemplate, 'simple')).toBe(true);
        expect(matchesDifficulty(taskTemplate, 'advanced')).toBe(false);
        expect(matchesDifficulty(taskTemplate, null)).toBe(true);
    });
});

describe('matchesFrequency', () => {
    it('matches daily/weekly/unlimited', () => {
        expect(matchesFrequency(taskTemplate, 'daily')).toBe(true);
        expect(matchesFrequency(taskTemplate, 'weekly')).toBe(false);
        expect(matchesFrequency({ frequencyLimit: 0, frequencyPeriod: 'week' }, 'unlimited')).toBe(true);
    });
});

describe('matchesPurchase', () => {
    it('filters purchase vs non-purchase rewards', () => {
        expect(matchesPurchase({ groupKey: 'family', tags: ['family'] }, 'together')).toBe(true);
        expect(matchesPurchase({ groupKey: 'purchases', tags: ['purchases'] }, 'none')).toBe(false);
        expect(matchesPurchase({ groupKey: 'purchases', tags: ['purchases'] }, 'purchase')).toBe(true);
    });
});

describe('matchesSearch', () => {
    it('matches title, comment, group and tags', () => {
        expect(matchesSearch(taskTemplate, 'умыться')).toBe(true);
        expect(matchesSearch(taskTemplate, 'утро')).toBe(true);
        expect(matchesSearch(taskTemplate, 'zzz')).toBe(false);
    });
});

describe('filterCatalog', () => {
    it('applies age, difficulty, frequency and search together', () => {
        const result = filterCatalog([taskTemplate, rewardTemplate], EMPTY_FILTERS, '');
        expect(result).toHaveLength(2);
        const filtered = filterCatalog([taskTemplate, rewardTemplate], { ...EMPTY_FILTERS, age: '9-11' }, '');
        expect(filtered).toHaveLength(0);
    });
});

describe('catalogGroups', () => {
    it('returns unique group names in order of appearance', () => {
        expect(catalogGroups([taskTemplate, rewardTemplate])).toEqual(['Утро и вечер', 'Время с семьёй']);
    });
});

describe('nonAgeFilterCount', () => {
    it('counts only non-age active filters', () => {
        expect(nonAgeFilterCount({ ...EMPTY_FILTERS, age: '6-8' })).toBe(0);
        expect(nonAgeFilterCount({ ...EMPTY_FILTERS, difficulty: 'simple', frequency: 'daily' })).toBe(2);
    });
});

describe('isAlreadyAdded', () => {
    it('detects a copy by sourceCatalogItemId', () => {
        const family = [{ id: 1, name: 'Умыться', sourceCatalogItemId: 'ct-6-8-1' }];
        expect(isAlreadyAdded(taskTemplate, family)).toBe(true);
    });
    it('detects a copy by normalized title', () => {
        const family = [{ id: 1, name: '🌅 Умыться, одеться и причесаться' }];
        expect(isAlreadyAdded(taskTemplate, family)).toBe(true);
    });
    it('returns false when not added', () => {
        expect(isAlreadyAdded(taskTemplate, [])).toBe(false);
    });
});

describe('mapGroupKeyToFamily', () => {
    it('maps a known built-in group key to an existing family group', () => {
        expect(mapGroupKeyToFamily('morning', ['Утро и вечер'])).toBe('Утро и вечер');
    });
    it('returns null when the family group does not exist yet', () => {
        expect(mapGroupKeyToFamily('morning', [])).toBeNull();
    });
});

describe('formatFrequency', () => {
    it('formats day/week/month', () => {
        expect(formatFrequency(1, 'day')).toBe('1 раз в день');
        expect(formatFrequency(2, 'week')).toBe('2 раза в неделю');
        expect(formatFrequency(3, 'month')).toBe('3 раз в месяц');
    });
});

describe('templateToTask / templateToReward', () => {
    it('copies a task template into a family-owned task', () => {
        const task = templateToTask(taskTemplate, 'Утро и вечер');
        expect(task.name).toBe(taskTemplate.title);
        expect(task.coins).toBe(1);
        expect(task.groupName).toBe('Утро и вечер');
        expect(task.sourceCatalogItemId).toBe('ct-6-8-1');
        expect(task.frequency).toEqual({ limit: 1, period: 'day' });
    });
    it('copies a reward template into a family-owned reward', () => {
        const reward = templateToReward(rewardTemplate, 'Время с семьёй');
        expect(reward.name).toBe(rewardTemplate.title);
        expect(reward.price).toBe(4);
        expect(reward.sourceCatalogItemId).toBe('cr-6-8-1');
    });
});
