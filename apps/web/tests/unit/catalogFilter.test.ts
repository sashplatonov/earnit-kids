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
} from '../../src/lib/telegram/services/catalogFilter';
import type { CatalogTaskTemplate, Task } from '../../src/lib/stores/app';
import type { CatalogRewardTemplate } from '../../src/lib/telegram/stores/types';

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
        const family = [{ id: 1, name: 'Умыться', sourceCatalogItemId: 'ct-6-8-1' } as unknown as Task];
        expect(isAlreadyAdded(taskTemplate, family)).toBe(true);
    });
    it('detects a copy by normalized title', () => {
        const family = [{ id: 1, name: '🌅 Умыться, одеться и причесаться' } as unknown as Task];
        expect(isAlreadyAdded(taskTemplate, family)).toBe(true);
    });
    it('returns false when not added', () => {
        expect(isAlreadyAdded(taskTemplate, [])).toBe(false);
    });
});

describe('mapGroupKeyToFamily', () => {
    it('matches the localized server label for both catalog locales', () => {
        expect(mapGroupKeyToFamily('morning', ['Morning & Evening'], 'Morning & Evening', 'task')).toBe('Morning & Evening');
        expect(mapGroupKeyToFamily('morning', ['Утро и вечер'], 'Утро и вечер', 'task')).toBe('Утро и вечер');
    });
    it('reuses a legacy Russian built-in group without relabeling it', () => {
        expect(mapGroupKeyToFamily('creativity', ['Творчество'], 'Creativity & Games', 'reward')).toBe('Творчество');
    });
    it('does not match another catalog kind or a missing family group', () => {
        expect(mapGroupKeyToFamily('morning', ['Morning & Evening'], 'Утро и вечер', 'reward')).toBeNull();
        expect(mapGroupKeyToFamily('morning', [], 'Morning & Evening', 'task')).toBeNull();
    });
    it('does not translate or rename a custom group', () => {
        expect(mapGroupKeyToFamily('morning', ['Morning & Evening', 'My mornings'], 'Morning & Evening', 'task')).toBe('Morning & Evening');
        expect(mapGroupKeyToFamily('morning', ['My mornings'], 'Morning & Evening', 'task')).toBeNull();
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
