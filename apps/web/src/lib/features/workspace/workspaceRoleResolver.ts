export type WorkspaceRole = 'parent' | 'child';

export function resolveWorkspaceRole(role: string): WorkspaceRole {
    return role === 'parent' || role === 'admin' ? 'parent' : 'child';
}
