export { DEMO_TABS, normalizeDemoTab } from './demo-data.js';
export function formatDemoDate(value: string | number | Date, locale: string): string;
export function formatDemoAmount(amount: number, locale: string): string;
export function resolveDemoTabState(href: string): {
    tab: (typeof DEMO_TABS)[number];
    shouldReplace: boolean;
    canonicalHref: string | null;
};
export function renderDemo(documentRef?: Document, windowRef?: Window): void;
export function selectDemoTab(
    documentRef: Document,
    windowRef: Window,
    tab: string | null | undefined,
    focus?: boolean,
): void;
