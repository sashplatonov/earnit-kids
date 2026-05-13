import type { MembershipPermission } from '$lib/types/auth';

export interface SessionSnapshot {
    authenticated: boolean;
    role?: string;
    familyId?: string | null;
    familyName?: string | null;
    childName?: string | null;
    email?: string | null;
    permission?: MembershipPermission | null;
    [key: string]: unknown;
}
