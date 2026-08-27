<script lang="ts">
    import WorkspaceRoleResolver from '$lib/features/workspace/WorkspaceRoleResolver.svelte';
    import type { MembershipPermission } from '$lib/types/auth';

    interface Props {
        role: string;
        publicOrigin?: string;
        permission?: MembershipPermission | null;
    }

    let { role, publicOrigin = '', permission = null }: Props = $props();

</script>

<WorkspaceRoleResolver {role} {permission} {publicOrigin} allowPreview showBrowserPush={false} />
