import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { DEMO_TABS, demoData, normalizeDemoTab } from '../../scripts/public-site/demo-data.js';
import { messages } from '../../scripts/public-site/i18n.js';
import { formatDemoAmount } from '../../scripts/public-site/demo.js';

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
        expect(formatDemoAmount(12, 'en')).toContain('+12');
        expect(formatDemoAmount(12, 'ru')).toContain('+12');
    });

    it('keeps every fixture display value in the equal static locale catalogs', () => {
        expect(messages.en.demo.fixture).toEqual({
            taskNames: { reading: 'Read for 15 minutes', desk: 'Clear your desk', plants: 'Water the plants' },
            rewardNames: { film: 'Choose the family film', game: 'Pick a board game', treat: 'Choose a weekend treat' },
            groups: { learning: 'Learning', home: 'Home', familyTime: 'Family time', smallJoys: 'Small joys' },
            repeats: { daily: 'Daily', weekdays: 'Weekdays', weekly: 'Weekly' },
            availability: { yes: 'Yes', no: 'No' },
        });
        expect(Object.keys(messages.en.demo.fixture)).toEqual(Object.keys(messages.ru.demo.fixture));
        expect(demoData.tasks.every((task) => task.nameKey && task.groupKey && task.repeatKey)).toBe(true);
        expect(demoData.rewards.every((reward) => reward.nameKey && reward.groupKey)).toBe(true);
    });

    it('keeps the static module isolated from network, app, and mutation behavior', () => {
        const source = readFileSync(resolve(process.cwd(), 'scripts/public-site/demo.js'), 'utf8');
        expect(source).not.toMatch(/\$lib|\bfetch\b|localStorage|sessionStorage|serviceWorker|\/api\/(?!login-google\/start)/);
        expect(source).not.toMatch(/\b(approve|reject|purchase|delete|retry)\b/i);
    });
});
