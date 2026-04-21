import { browser } from '$app/environment';

export type CardViewMode = 'grid' | 'list';
export type CardViewSection = 'tasks' | 'shop';
export type CardViewRole = 'admin' | 'child';

const CARD_VIEW_MODE_KEY_PREFIX = 'earnit-card-view';

function storageKey(section: CardViewSection, role: CardViewRole): string {
    return `${CARD_VIEW_MODE_KEY_PREFIX}:${section}:${role}`;
}

export function loadCardViewMode(section: CardViewSection, role: CardViewRole): CardViewMode {
    if (!browser) {
        return 'grid';
    }

    try {
        return localStorage.getItem(storageKey(section, role)) === 'list' ? 'list' : 'grid';
    } catch {
        return 'grid';
    }
}

export function saveCardViewMode(section: CardViewSection, role: CardViewRole, mode: CardViewMode): void {
    if (!browser) {
        return;
    }

    try {
        localStorage.setItem(storageKey(section, role), mode);
    } catch {
        // ignore storage failures and keep the in-memory view mode
    }
}