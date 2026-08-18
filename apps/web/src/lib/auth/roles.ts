export function isAdminRole(role?: string): boolean {
    return role === 'admin' || role === 'parent' || role === 'super_admin';
}
