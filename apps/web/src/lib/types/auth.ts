export type MembershipPermission = 'viewer' | 'editor' | 'family_admin';

export interface FamilyChoice {
    familyId: string;
    familyName: string;
    permission: MembershipPermission;
}

export interface ParentMembership {
    id: number;
    email: string;
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
