const freeze = (value) => Object.freeze(value);

export const DEMO_TABS = freeze(['tasks', 'rewards', 'history', 'requests']);

export const demoData = freeze({
    child: freeze({ id: 'demo-child-anna', name: 'Anna', balance: 42 }),
    tasks: freeze([
        freeze({ id: 'task-reading', nameKey: 'reading', groupKey: 'learning', repeatKey: 'daily', coins: 3 }),
        freeze({ id: 'task-desk', nameKey: 'desk', groupKey: 'home', repeatKey: 'weekdays', coins: 2 }),
        freeze({ id: 'task-plants', nameKey: 'plants', groupKey: 'home', repeatKey: 'weekly', coins: 4 }),
    ]),
    rewards: freeze([
        freeze({ id: 'reward-film', nameKey: 'film', groupKey: 'familyTime', price: 12, available: true }),
        freeze({ id: 'reward-game', nameKey: 'game', groupKey: 'familyTime', price: 25, available: true }),
        freeze({ id: 'reward-treat', nameKey: 'treat', groupKey: 'smallJoys', price: 50, available: false }),
    ]),
    history: freeze([
        freeze({ id: 'history-reading', kind: 'earned', amount: 3, date: '2026-08-24T16:30:00Z', labelKey: 'reading' }),
        freeze({ id: 'history-film', kind: 'spent', amount: 12, date: '2026-08-22T18:00:00Z', labelKey: 'film' }),
        freeze({ id: 'history-desk', kind: 'earned', amount: 2, date: '2026-08-21T15:00:00Z', labelKey: 'desk' }),
    ]),
    requests: freeze([
        freeze({ id: 'request-task', kind: 'task', amount: 3, date: '2026-08-25T16:30:00Z', status: 'pending', labelKey: 'reading' }),
        freeze({ id: 'request-reward', kind: 'reward', amount: 12, date: '2026-08-22T18:00:00Z', status: 'approved', labelKey: 'film' }),
        freeze({ id: 'request-rejected', kind: 'task', amount: 2, date: '2026-08-20T15:00:00Z', status: 'rejected', labelKey: 'desk' }),
    ]),
});

export function normalizeDemoTab(value) {
    return DEMO_TABS.includes(value) ? value : 'tasks';
}
