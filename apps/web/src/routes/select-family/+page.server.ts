import type { PageServerLoad } from './$types';
import { getI18nPayloadForPath } from '$lib/i18n';

export const load: PageServerLoad = async ({ fetch, locals }) => {
    const response = await fetch('/api/select-family', {
        headers: { accept: 'application/json' },
    });

    return {
        choices: response.ok
            ? ((await response.json()) as { familyChoices?: Array<{
                familyId: string;
                familyName: string;
                permission: string;
                blocked: boolean;
            }> }).familyChoices ?? []
            : [],
        expired: !response.ok,
        i18n: await getI18nPayloadForPath('/select-family', locals.locale),
    };
};
