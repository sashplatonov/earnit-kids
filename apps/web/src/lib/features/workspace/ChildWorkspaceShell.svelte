<script lang="ts">
    import TelegramChildShell from '$lib/components/telegram/TelegramChildShell.svelte';

    export let publicOrigin = '';
    export let onExitPreview: () => void = () => {};
    export let showSessionActions = false;
</script>

<TelegramChildShell {publicOrigin} {onExitPreview} {showSessionActions} />
