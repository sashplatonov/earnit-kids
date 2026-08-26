const freeze = (value) => Object.freeze(value);

export const DEMO_TABS = freeze(['tasks', 'rewards', 'history', 'requests']);

export const demoData = freeze({
    child: freeze({ id: 'demo-child-anna', name: 'Anna', balance: 42 }),
    tasks: freeze([
        freeze({ id: 'task-reading', name: freeze({ en: 'Read for 15 minutes', ru: 'Читать 15 минут' }), group: 'Learning', repeat: 'Daily', coins: 3 }),
        freeze({ id: 'task-desk', name: freeze({ en: 'Clear your desk', ru: 'Убрать свой стол' }), group: 'Home', repeat: 'Weekdays', coins: 2 }),
        freeze({ id: 'task-plants', name: freeze({ en: 'Water the plants', ru: 'Полить растения' }), group: 'Home', repeat: 'Weekly', coins: 4 }),
    ]),
    rewards: freeze([
        freeze({ id: 'reward-film', name: freeze({ en: 'Choose the family film', ru: 'Выбрать семейный фильм' }), group: 'Family time', price: 12, available: true }),
        freeze({ id: 'reward-game', name: freeze({ en: 'Pick a board game', ru: 'Выбрать настольную игру' }), group: 'Family time', price: 25, available: true }),
        freeze({ id: 'reward-treat', name: freeze({ en: 'Choose a weekend treat', ru: 'Выбрать угощение на выходных' }), group: 'Small joys', price: 50, available: false }),
    ]),
    history: freeze([
        freeze({ id: 'history-reading', kind: 'earned', amount: 3, date: '2026-08-24T16:30:00Z', label: freeze({ en: 'Read for 15 minutes', ru: 'Читать 15 минут' }) }),
        freeze({ id: 'history-film', kind: 'spent', amount: 12, date: '2026-08-22T18:00:00Z', label: freeze({ en: 'Choose the family film', ru: 'Выбрать семейный фильм' }) }),
        freeze({ id: 'history-desk', kind: 'earned', amount: 2, date: '2026-08-21T15:00:00Z', label: freeze({ en: 'Clear your desk', ru: 'Убрать свой стол' }) }),
    ]),
    requests: freeze([
        freeze({ id: 'request-task', kind: 'task', amount: 3, date: '2026-08-25T16:30:00Z', status: 'pending', label: freeze({ en: 'Read for 15 minutes', ru: 'Читать 15 минут' }) }),
        freeze({ id: 'request-reward', kind: 'reward', amount: 12, date: '2026-08-22T18:00:00Z', status: 'approved', label: freeze({ en: 'Choose the family film', ru: 'Выбрать семейный фильм' }) }),
        freeze({ id: 'request-rejected', kind: 'task', amount: 2, date: '2026-08-20T15:00:00Z', status: 'rejected', label: freeze({ en: 'Clear your desk', ru: 'Убрать свой стол' }) }),
    ]),
});

export function normalizeDemoTab(value) {
    return DEMO_TABS.includes(value) ? value : 'tasks';
}
