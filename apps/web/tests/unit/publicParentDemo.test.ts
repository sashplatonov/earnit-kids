import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { DEMO_TABS, demoData, normalizeDemoTab } from '../../scripts/public-site/demo-data.js';
import { messages } from '../../scripts/public-site/i18n.js';
import { formatDemoAmount, resolveDemoTabState } from '../../scripts/public-site/demo.js';

describe('public parent demo', () => {
    it('contains the complete immutable scenario', () => {
        expect(DEMO_TABS).toEqual(['tasks', 'rewards', 'history', 'requests']);
        expect(demoData.tasks.length).toBeGreaterThan(1);
        expect(demoData.rewards.length).toBeGreaterThan(1);
        expect(demoData.history.some((entry) => entry.kind === 'earned')).toBe(true);
        expect(demoData.history.some((entry) => entry.kind === 'spent')).toBe(true);
        expect(new Set(demoData.requests.map((request) => request.status))).toEqual(new Set(['pending', 'approved', 'rejected']));
        expect(Object.isFrozen(demoData)).toBe(true);
    });

    it('normalizes tab state and formats signed amounts by locale', () => {
        expect(normalizeDemoTab('rewards')).toBe('rewards');
        expect(normalizeDemoTab('unknown')).toBe('tasks');
        expect(resolveDemoTabState('https://example.test/ru/demo.html?tab=not-a-tab&source=review#overview')).toEqual({
            tab: 'tasks',
            shouldReplace: true,
            canonicalHref: 'https://example.test/ru/demo.html?tab=tasks&source=review#overview',
        });
        expect(resolveDemoTabState('https://example.test/demo.html?tab=rewards&source=review#overview')).toEqual({
            tab: 'rewards',
            shouldReplace: false,
            canonicalHref: null,
        });
        expect(formatDemoAmount(12, 'en')).toContain('+12');
        expect(formatDemoAmount(12, 'ru')).toContain('+12');
    });

    it('keeps every fixture display value in the equal static locale catalogs', () => {
        const englishFixture = (messages.en.demo as { fixture: Record<string, unknown> }).fixture;
        const russianFixture = (messages.ru.demo as { fixture: Record<string, unknown> }).fixture;
        expect(englishFixture).toEqual({
            taskNames: { reading: 'Read for 15 minutes', desk: 'Clear your desk', plants: 'Water the plants' },
            rewardNames: { film: 'Choose the family film', game: 'Pick a board game', treat: 'Choose a weekend treat' },
            groups: { learning: 'Learning', home: 'Home', familyTime: 'Family time', smallJoys: 'Small joys' },
            repeats: { daily: 'Daily', weekdays: 'Weekdays', weekly: 'Weekly' },
            availability: { yes: 'Yes', no: 'No' },
        });
        expect(Object.keys(englishFixture)).toEqual(Object.keys(russianFixture));
        expect(demoData.tasks.every((task) => task.nameKey && task.groupKey && task.repeatKey)).toBe(true);
        expect(demoData.rewards.every((reward) => reward.nameKey && reward.groupKey)).toBe(true);
    });

    it('keeps the static module isolated from network, app, and mutation behavior', () => {
        const source = readFileSync(resolve(process.cwd(), 'scripts/public-site/demo.js'), 'utf8');
        expect(source).not.toMatch(/\$lib|\bfetch\b|localStorage|sessionStorage|serviceWorker|\/api\/(?!login-google\/start)/);
        expect(source).not.toMatch(/\b(approve|reject|purchase|delete|retry)\b/i);
    });
});
