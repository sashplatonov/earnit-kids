import { describe, expect, it } from 'vitest';
import { sanitizeReadyCatalogTelemetry } from '../../src/lib/services/readyCatalogTelemetry';

describe('sanitizeReadyCatalogTelemetry', () => {
    it('accepts a valid catalog_opened event', () => {
        expect(sanitizeReadyCatalogTelemetry({ name: 'catalog_opened', type: 'TASK' })).toEqual({
            name: 'catalog_opened',
            type: 'TASK',
        });
    });

    it('accepts optional group key and bulk count', () => {
        expect(sanitizeReadyCatalogTelemetry({
            name: 'catalog_bulk_add',
            type: 'REWARD',
            catalogGroupKey: 'family',
            bulkCount: 3,
        })).toEqual({
            name: 'catalog_bulk_add',
            type: 'REWARD',
            catalogGroupKey: 'family',
            bulkCount: 3,
        });
    });

    it('rejects unknown event names', () => {
        expect(sanitizeReadyCatalogTelemetry({ name: 'nope', type: 'TASK' })).toBeNull();
    });

    it('rejects invalid type', () => {
        expect(sanitizeReadyCatalogTelemetry({ name: 'catalog_opened', type: 'OTHER' })).toBeNull();
    });

    it('drops non-finite bulk counts', () => {
        expect(sanitizeReadyCatalogTelemetry({ name: 'catalog_bulk_add', type: 'TASK', bulkCount: NaN })).toEqual({
            name: 'catalog_bulk_add',
            type: 'TASK',
        });
    });
});
