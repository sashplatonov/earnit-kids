import { error, redirect } from '@sveltejs/kit';
import { isAdminRole } from '$lib/app/routes';
import { buildI18nPayload, localizePath, translateKey } from '$lib/i18n';
import { normalizeServerData } from '$lib/services/serverContract';
import type { PageServerLoad } from './$types';

type LoadEvent = Parameters<PageServerLoad>[0];

async function fetchCatalog(fetchFn: LoadEvent['fetch'], childId?: string | null) {
    const query = childId ? `?childId=${encodeURIComponent(childId)}` : '';
    const response = await fetchFn(`/api/data${query}`);

    if (response.status === 401) {
        return { unauthorized: true } as const;
    }

    if (!response.ok) {
        throw error(response.status, 'Failed to load print catalog');
    }

    return {
        unauthorized: false,
        payload: (await response.json()) as Record<string, unknown>,
    } as const;
}

export const load: PageServerLoad = async ({ locals, url, fetch }) => {
    if (!locals.session.authenticated) {
        throw redirect(302, localizePath('/login', locals.locale));
    }

    const requestedChildId = url.searchParams.get('childId');
    const initial = await fetchCatalog(fetch, requestedChildId);
    if (initial.unauthorized) {
        throw redirect(302, localizePath('/login', locals.locale));
    }

    let payload = initial.payload;
    let resolvedChildId = requestedChildId;

    if (isAdminRole(locals.session.role) && requestedChildId == null) {
        const children = Array.isArray(payload.children) ? payload.children : [];
        const fallbackChildId = payload.lastSelectedChildId ?? (children[0] as Record<string, unknown> | undefined)?.id ?? null;

        if (fallbackChildId != null) {
            const fallback = await fetchCatalog(fetch, String(fallbackChildId));
            if (!fallback.unauthorized) {
                payload = fallback.payload;
                resolvedChildId = String(fallbackChildId);
            }
        }
    }

    const normalized = normalizeServerData(payload);
    const i18n = buildI18nPayload(locals.locale, ['common']);

    return {
        metaTitle: translateKey(i18n, 'common.printCatalog'),
        childId: resolvedChildId,
        childName: normalized.childNickname ?? '',
        isAdmin: isAdminRole(locals.session.role),
        tasks: normalized.tasks,
        shopItems: normalized.shop,
        children: normalized.children,
    };
};
