export type MembershipPermission = 'viewer' | 'editor' | 'family_admin';

export interface FamilyChoice {
    familyId: string;
    familyName: string;
    permission: MembershipPermission;
    blocked: boolean;
}

export interface ParentMembership {
    id: number;
    email: string | null;
    displayName: string | null;
    telegramUserId: number | null;
    telegramUsername: string | null;
    telegramDisplayName: string | null;
    permission: MembershipPermission;
    status: string;
}

export interface AuthResponseSnapshot {
    success: boolean;
    role: string | null;
    familyId: string | null;
    childId: number | null;
    childName: string | null;
    error: string | null;
    selectionRequired: boolean;
    familyChoices: FamilyChoice[] | null;
}
