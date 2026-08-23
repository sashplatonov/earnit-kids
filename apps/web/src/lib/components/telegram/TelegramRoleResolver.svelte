<script lang="ts">
    import WorkspaceRoleResolver from '$lib/features/workspace/WorkspaceRoleResolver.svelte';

    export let role: string;
    export let publicOrigin = '';

</script>

<WorkspaceRoleResolver {role} {publicOrigin} allowPreview showBrowserPush={false} browserPushEnabled={false} />
