import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../src/lib/services/bootstrap', () => ({
    refreshData: vi.fn().mockResolvedValue(true),
}));

import { refreshData } from '../../src/lib/services/bootstrap';
import { startWebSocket, stopWebSocket } from '../../src/lib/services/websocket';

class FakeWebSocket {
    static latest: FakeWebSocket | null = null;

    readonly listeners = new Map<string, Array<(event: Event) => void>>();

    constructor(url: string) {
        void url;
        FakeWebSocket.latest = this;
    }

    addEventListener(type: string, listener: (event: Event) => void) {
        this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
    }

    close() {}

    emitMessage(payload: unknown) {
        const event = new MessageEvent('message', { data: JSON.stringify(payload) });
        this.listeners.get('message')?.forEach((listener) => listener(event));
    }
}

describe('websocket data reconciliation', () => {
    afterEach(() => {
        stopWebSocket();
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
        FakeWebSocket.latest = null;
    });

    it('refreshes the server snapshot for backend DATA_UPDATED events', async () => {
        vi.stubGlobal('location', { protocol: 'https:', host: 'example.test' });
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ token: 'test' }))));
        vi.stubGlobal('WebSocket', FakeWebSocket);

        startWebSocket();
        await vi.waitFor(() => expect(FakeWebSocket.latest).not.toBeNull());

        FakeWebSocket.latest?.emitMessage({ type: 'DATA_UPDATED', payload: { childId: 10 } });

        await vi.waitFor(() => expect(refreshData).toHaveBeenCalledOnce());
    });
});
