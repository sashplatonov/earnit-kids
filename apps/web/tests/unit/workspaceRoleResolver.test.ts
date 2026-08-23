import { describe, expect, it } from 'vitest';
import { resolveWorkspaceRole } from '../../src/lib/features/workspace/workspaceRoleResolver';

describe('resolveWorkspaceRole', () => {
    it('routes parent and admin sessions to the parent workspace', () => {
        expect(resolveWorkspaceRole('parent')).toBe('parent');
        expect(resolveWorkspaceRole('admin')).toBe('parent');
    });

    it('routes child sessions to the child workspace', () => {
        expect(resolveWorkspaceRole('child')).toBe('child');
        expect(resolveWorkspaceRole('')).toBe('child');
    });
});
