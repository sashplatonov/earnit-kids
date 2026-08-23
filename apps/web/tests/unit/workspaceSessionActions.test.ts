import { describe, expect, it, vi } from 'vitest';
import { logout } from '../../src/lib/services/api';

describe('workspace session actions', () => {
    it('uses the CSRF-aware logout endpoint and reports success', async () => {
        const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 204 }));
        vi.stubGlobal('fetch', fetchMock);
        vi.stubGlobal('document', { cookie: 'csrf_token=session-csrf' });

        await expect(logout()).resolves.toBe(true);
        expect(fetchMock).toHaveBeenCalledWith('/api/logout', expect.objectContaining({
            credentials: 'same-origin',
            method: 'POST',
            headers: expect.objectContaining({ 'X-CSRF-Token': 'session-csrf' }),
        }));

        vi.unstubAllGlobals();
    });

    it('keeps the workspace active when logout fails', async () => {
        const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 403 }));
        vi.stubGlobal('fetch', fetchMock);

        await expect(logout()).resolves.toBe(false);
        expect(fetchMock).toHaveBeenCalledTimes(1);

        vi.unstubAllGlobals();
    });
});
