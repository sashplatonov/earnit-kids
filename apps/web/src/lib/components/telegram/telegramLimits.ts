/** Shared business rules for child coin limit steppers (Mini App). */
export const MAX_CHILD_LIMIT = 10000;

/** Clamp a maximum value to the valid range [0, MAX_CHILD_LIMIT]. */
export function clampLimit(value: number): number {
    return Math.max(0, Math.min(MAX_CHILD_LIMIT, Math.round(value)));
}

/** Apply a ±1/±5 step to a maximum, never letting it go negative. */
export function stepLimit(value: number, delta: number): number {
    return clampLimit(value + delta);
}

/** Derive the persisted limit value: 0 when the toggle is off, else the max. */
export function effectiveLimit(enabled: boolean, max: number): number {
    return enabled ? clampLimit(max) : 0;
}
