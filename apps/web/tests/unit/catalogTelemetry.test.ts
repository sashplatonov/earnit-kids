import { describe, expect, it } from 'vitest';
import { sanitizeCatalogTelemetry } from '$lib/services/catalogTelemetry';

describe('catalog telemetry privacy boundary', () => {
    it('keeps only aggregate allowlisted fields', () => {
        expect(sanitizeCatalogTelemetry({ name: 'task_action', surface: 'tasks', result: 'success', taskTitle: 'secret' }))
            .toEqual({ name: 'task_action', surface: 'tasks', result: 'success' });
    });

    it('rejects unknown event values', () => {
        expect(sanitizeCatalogTelemetry({ name: 'task_comment', surface: 'tasks', result: 'success' })).toBeNull();
    });
});
