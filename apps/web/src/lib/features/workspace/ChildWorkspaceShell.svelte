<script lang="ts">
    import TelegramChildShell from '$lib/components/telegram/TelegramChildShell.svelte';

    export let publicOrigin = '';
    export let onExitPreview: () => void = () => {};
</script>

<TelegramChildShell {publicOrigin} {onExitPreview} />
