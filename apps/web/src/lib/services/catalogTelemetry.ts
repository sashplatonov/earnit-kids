export type CatalogTelemetryName = 'task_action' | 'shop_action' | 'reward_goal_action';

export interface CatalogTelemetryEvent {
    name: CatalogTelemetryName;
    surface: 'tasks' | 'shop';
    result: 'started' | 'success' | 'error';
}

const allowedNames = new Set<CatalogTelemetryName>(['task_action', 'shop_action', 'reward_goal_action']);

export function sanitizeCatalogTelemetry(input: Record<string, unknown>): CatalogTelemetryEvent | null {
    if (!allowedNames.has(input.name as CatalogTelemetryName)) return null;
    if (input.surface !== 'tasks' && input.surface !== 'shop') return null;
    if (input.result !== 'started' && input.result !== 'success' && input.result !== 'error') return null;
    return { name: input.name as CatalogTelemetryName, surface: input.surface, result: input.result };
}

export function recordCatalogEvent(input: Record<string, unknown>): void {
    if (typeof window === 'undefined') return;
    const event = sanitizeCatalogTelemetry(input);
    if (event) window.dispatchEvent(new CustomEvent('catalog:telemetry', { detail: event }));
}
