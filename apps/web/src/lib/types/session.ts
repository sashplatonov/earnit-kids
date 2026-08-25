import type { MembershipPermission } from '$lib/types/auth';

export interface SessionSnapshot {
    authenticated: boolean;
    role?: string;
    familyId?: string | null;
    familyName?: string | null;
    childName?: string | null;
    email?: string | null;
    permission?: MembershipPermission | null;
    locale?: 'en' | 'ru' | null;
    languageSetupRequired?: boolean;
    [key: string]: unknown;
}
