<script lang="ts">
    import TelegramChildShell from '$lib/components/telegram/TelegramChildShell.svelte';
    import LocaleSwitcher from '$lib/components/LocaleSwitcher.svelte';

    export let publicOrigin = '';
    export let onExitPreview: () => void = () => {};
    export let showSessionActions = false;
    export let showFamilyLocale = false;
</script>

{#if showFamilyLocale}<div class="family-locale"><LocaleSwitcher familyManaged readOnly compact /></div>{/if}
<TelegramChildShell {publicOrigin} {onExitPreview} {showSessionActions} />

<style>
    .family-locale{display:flex;justify-content:flex-end;min-width:0}
    @media(max-width:520px){.family-locale{justify-content:stretch}.family-locale :global(.locale-switcher){justify-content:space-between;width:100%}}
</style>
