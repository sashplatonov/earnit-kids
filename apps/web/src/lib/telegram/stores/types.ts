export interface TaskPeriodProgress {
    period: string;
    completed: number;
    pending: number;
    limit: number;
    remaining: number;
    available: boolean;
    windowStart: string;
    resetAt: string;
}

export interface ShopItem {
    id: number | string;
    name: string;
    price: number;
    isActive?: boolean;
    groupName?: string | null;
    icon?: string | null;
    comment?: string | null;
    moneyLimit?: number | null;
    frequency?: { period?: string; limit?: number } | null;
    ageMin?: number | null;
    ageMax?: number | null;
    lastPurchasedAt?: string | null;
    periodProgress?: TaskPeriodProgress | null;
    [key: string]: unknown;
}

export interface CatalogRewardTemplate {
    id: string;
    title: string;
    comment?: string | null;
    price: number;
    groupKey: string;
    groupName: string;
    semanticGraphicKey?: string | null;
    frequencyLimit?: number | null;
    frequencyPeriod?: string | null;
    minAge?: number | null;
    maxAge?: number | null;
    difficulty?: string | null;
    tags?: string[];
    active?: boolean;
    sortOrder?: number;
    [key: string]: unknown;
}