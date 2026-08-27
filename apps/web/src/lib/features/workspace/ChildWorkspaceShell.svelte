<script lang="ts">
    import TelegramChildShell from '$lib/components/telegram/TelegramChildShell.svelte';

    interface Props {
        publicOrigin?: string;
        onExitPreview?: () => void;
        showSessionActions?: boolean;
    }

    let { publicOrigin = '', onExitPreview = () => {}, showSessionActions = false }: Props = $props();
</script>

<TelegramChildShell {publicOrigin} {onExitPreview} {showSessionActions} />
