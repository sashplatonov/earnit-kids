import { fail, redirect } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals }) => {
    // Verify admin access server-side
    const session = await locals.getSession();
    
    if (!session) {
        throw redirect(303, '/login');
    }

    // Check if user has admin privileges
    const telegramUserId = session.telegram_user_id;
    const adminUserIds = process.env.TELEGRAM_ADMIN_USER_IDS?.split(',').map(id => id.trim()).filter(Boolean) || [];
    
    if (!adminUserIds.includes(String(telegramUserId))) {
        throw redirect(303, '/app/settings');
    }

    return {
        adminUserIds,
        telegramUserId,
    };
};

export const actions: Actions = {
    default: async () => {
        return fail(400, { error: 'Not implemented' });
    },
};
