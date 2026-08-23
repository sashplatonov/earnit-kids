import { describe, expect, it, vi } from 'vitest';
import { resendParentInvitation, revokeParentInvitation } from '../../src/lib/services/api';

describe('workspace access API', () => {
    it('resends and revokes invitations through the authenticated API', async () => {
        const fetchMock = vi.fn<typeof fetch>()
            .mockResolvedValueOnce(new Response('{}', { status: 200 }))
            .mockResolvedValueOnce(new Response('{}', { status: 200 }));
        vi.stubGlobal('fetch', fetchMock);

        expect(await resendParentInvitation(12)).toMatchObject({ ok: true });
        expect(await revokeParentInvitation(12)).toMatchObject({ ok: true });
        expect(fetchMock.mock.calls.map(([url, init]) => [url, init?.method])).toEqual([
            ['/api/parents/invitations/12/resend', 'POST'],
            ['/api/parents/invitations/12/revoke', 'POST'],
        ]);
        vi.unstubAllGlobals();
    });
});
