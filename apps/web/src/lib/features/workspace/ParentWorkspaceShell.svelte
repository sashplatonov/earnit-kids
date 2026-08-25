<script lang="ts">
    import TelegramParentShell from '$lib/components/telegram/TelegramParentShell.svelte';
    import LocaleSwitcher from '$lib/components/LocaleSwitcher.svelte';
    import ParentAccessPanel from './family/ParentAccessPanel.svelte';

    export let publicOrigin = '';
    export let onViewAsChild: () => void = () => {};
    export let showAccessPanel = false;
    export let showSessionActions = false;
    export let showFamilyLocale = false;
    export let familyAdmin = false;
</script>

<div class="workspace-parent">
    {#if showFamilyLocale}<div class="family-locale"><LocaleSwitcher familyManaged readOnly={!familyAdmin} compact /></div>{/if}
    {#if showAccessPanel}<ParentAccessPanel />{/if}
    <TelegramParentShell {publicOrigin} {onViewAsChild} {showSessionActions} />
</div>

<style>
    .workspace-parent{display:grid;gap:1rem;min-width:0}
    .family-locale{display:flex;justify-content:flex-end;min-width:0}
    @media(max-width:520px){.workspace-parent{gap:.75rem}.family-locale{justify-content:stretch}.family-locale :global(.locale-switcher){justify-content:space-between;width:100%}}
</style>
