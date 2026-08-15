/**
 * Non-PII analytics for the parent ready catalog (CAT-013).
 *
 * Emits a `catalog:telemetry` CustomEvent with a sanitized payload. No free
 * text (task/reward titles) is ever sent — only type, group key and counts.
 */
export type ReadyCatalogEventName =
    | 'catalog_opened'
    | 'catalog_search_used'
    | 'catalog_filter_selected'
    | 'catalog_item_added'
    | 'catalog_bulk_add'
    | 'catalog_duplicate_skipped'
    | 'catalog_details_opened';

export type ReadyCatalogType = 'TASK' | 'REWARD';

export interface ReadyCatalogTelemetryEvent {
    name: ReadyCatalogEventName;
    type: ReadyCatalogType;
    catalogGroupKey?: string;
    bulkCount?: number;
}

const allowedNames = new Set<ReadyCatalogEventName>([
    'catalog_opened',
    'catalog_search_used',
    'catalog_filter_selected',
    'catalog_item_added',
    'catalog_bulk_add',
    'catalog_duplicate_skipped',
    'catalog_details_opened',
]);

export function sanitizeReadyCatalogTelemetry(input: Record<string, unknown>): ReadyCatalogTelemetryEvent | null {
    if (!allowedNames.has(input.name as ReadyCatalogEventName)) return null;
    if (input.type !== 'TASK' && input.type !== 'REWARD') return null;
    const event: ReadyCatalogTelemetryEvent = {
        name: input.name as ReadyCatalogEventName,
        type: input.type,
    };
    if (typeof input.catalogGroupKey === 'string' && input.catalogGroupKey) {
        event.catalogGroupKey = input.catalogGroupKey;
    }
    if (typeof input.bulkCount === 'number' && Number.isFinite(input.bulkCount)) {
        event.bulkCount = Math.max(0, Math.trunc(input.bulkCount));
    }
    return event;
}

export function recordReadyCatalogEvent(input: Record<string, unknown>): void {
    if (typeof window === 'undefined') return;
    const event = sanitizeReadyCatalogTelemetry(input);
    if (event) window.dispatchEvent(new CustomEvent('catalog:telemetry', { detail: event }));
}
