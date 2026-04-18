export interface SessionSnapshot {
    authenticated: boolean;
    role?: string;
    familyName?: string | null;
    childName?: string | null;
    email?: string | null;
    [key: string]: unknown;
}
