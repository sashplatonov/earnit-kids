export type DemoLocale = 'en' | 'ru';

export type DemoLocalizedText = Record<DemoLocale, string>;

export type DemoHistoryEntry = {
    id: string;
    kind: 'earned' | 'spent';
    amount: number;
    date: string;
    label: DemoLocalizedText;
};

export type DemoRequest = {
    id: string;
    kind: 'task' | 'reward';
    amount: number;
    date: string;
    status: 'pending' | 'approved' | 'rejected';
    label: DemoLocalizedText;
};

export type DemoData = {
    child: { id: string; name: string; balance: number };
    tasks: Array<{ id: string; name: DemoLocalizedText; group: string; repeat: string; coins: number }>;
    rewards: Array<{ id: string; name: DemoLocalizedText; group: string; price: number; available: boolean }>;
    history: DemoHistoryEntry[];
    requests: DemoRequest[];
};

export const DEMO_TABS: readonly ['tasks', 'rewards', 'history', 'requests'];
export const demoData: DemoData;
export function normalizeDemoTab(value: string | null | undefined): (typeof DEMO_TABS)[number];
