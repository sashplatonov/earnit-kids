import { describe, expect, it } from 'vitest';

import { buildPrintCatalogUrl } from '../../src/lib/services/printCatalog';
import { load as printCatalogLoad } from '../../src/routes/print/catalog/+page.server';

describe('buildPrintCatalogUrl', () => {
    it('returns the base path for child sessions', () => {
        expect(buildPrintCatalogUrl('/en/print/catalog', null)).toBe('/en/print/catalog');
    });

    it('appends the selected child id for admin sessions', () => {
        expect(buildPrintCatalogUrl('/ru/print/catalog', 42)).toBe('/ru/print/catalog?childId=42');
    });
});

describe('print catalog route', () => {
    it('loads the selected child data for admin sessions', async () => {
        const result = await printCatalogLoad({
            locals: {
                locale: 'en',
                session: {
                    authenticated: true,
                    role: 'parent',
                },
            },
            url: new URL('http://localhost/en/print/catalog?childId=7'),
            fetch: async (input: RequestInfo | URL) => {
                expect(String(input)).toBe('/api/data?childId=7');
                return new Response(JSON.stringify({
                    childNickname: 'Mia',
                    tasks: [{ id: 1, name: 'Read', coins: 5 }],
                    shop: [{ id: 2, name: 'Ice cream', price: 20 }],
                    children: [{ id: 7, nickname: 'Mia', balance: 0 }],
                }), {
                    status: 200,
                    headers: { 'Content-Type': 'application/json' },
                });
            },
        } as never) as {
            childId: string | null;
            childName: string;
            tasks: unknown[];
            shopItems: unknown[];
        };

        expect(result.childId).toBe('7');
        expect(result.childName).toBe('Mia');
        expect(result.tasks).toHaveLength(1);
        expect(result.shopItems).toHaveLength(1);
    });

    it('falls back to the persisted or first child when admin childId is missing', async () => {
        const requests: string[] = [];

        const result = await printCatalogLoad({
            locals: {
                locale: 'en',
                session: {
                    authenticated: true,
                    role: 'parent',
                },
            },
            url: new URL('http://localhost/en/print/catalog'),
            fetch: async (input: RequestInfo | URL) => {
                requests.push(String(input));

                if (requests.length === 1) {
                    return new Response(JSON.stringify({
                        lastSelectedChildId: 9,
                        children: [{ id: 9, nickname: 'Leo', balance: 0 }],
                        tasks: [],
                        shop: [],
                    }), {
                        status: 200,
                        headers: { 'Content-Type': 'application/json' },
                    });
                }

                return new Response(JSON.stringify({
                    childNickname: 'Leo',
                    children: [{ id: 9, nickname: 'Leo', balance: 0 }],
                    tasks: [{ id: 1, name: 'Clean room', coins: 10 }],
                    shop: [],
                }), {
                    status: 200,
                    headers: { 'Content-Type': 'application/json' },
                });
            },
        } as never) as {
            childId: string | null;
            tasks: unknown[];
        };

        expect(requests).toEqual(['/api/data', '/api/data?childId=9']);
        expect(result.childId).toBe('9');
        expect(result.tasks).toHaveLength(1);
    });
});
