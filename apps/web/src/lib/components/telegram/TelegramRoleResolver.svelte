<script lang="ts">
    import WorkspaceRoleResolver from '$lib/features/workspace/WorkspaceRoleResolver.svelte';
    import type { MembershipPermission } from '$lib/types/auth';

    export let role: string;
    export let publicOrigin = '';
    export let permission: MembershipPermission | null = null;

</script>

<WorkspaceRoleResolver {role} {permission} {publicOrigin} allowPreview showBrowserPush={false} />
