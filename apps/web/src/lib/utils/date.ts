export function toDate(value: unknown): Date | null {
    if (typeof value !== 'string' && !(value instanceof Date)) {
        return null;
    }

    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
}

export function startOfTodayTimestamp(referenceMs: number): number {
    const today = new Date(referenceMs);
    today.setHours(0, 0, 0, 0);
    return today.getTime();
}