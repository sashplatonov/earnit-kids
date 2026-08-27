<script lang="ts">
    import TelegramIcon from '$lib/components/telegram/TelegramIcon.svelte';

    interface Props {
        activeTooltip: string | null;
        content: Record<string, { title: string; body: string }>;
        close: () => void;
        closeLabel: string;
    }

    let {
        activeTooltip,
        content,
        close,
        closeLabel
    }: Props = $props();
</script>

{#if activeTooltip && content[activeTooltip]}
    <div class="tooltip-box" role="dialog" aria-label={content[activeTooltip].title}>
        <div class="tooltip-head">
            <b>{content[activeTooltip].title}</b>
            <button class="tooltip-close" type="button" aria-label={closeLabel} onclick={close}>
                <TelegramIcon name="close" size={15} />
            </button>
        </div>
        <p>{content[activeTooltip].body}</p>
    </div>
{/if}

<style>
    .tooltip-box { position: fixed; bottom: calc(78px + env(safe-area-inset-bottom)); left: 12px; right: 12px; z-index: 30; padding: 12px; border-radius: 12px; background: #172036; color: #fff; box-shadow: 0 8px 24px rgb(20 29 54 / 25%); }
    .tooltip-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
    .tooltip-head b { font-size: 11px; color: #ccd2ff; }
    .tooltip-box p { margin: 0; color: #e6e9f2; font-size: 11px; line-height: 1.45; }
    .tooltip-close { width: 44px; height: 44px; display: grid; place-items: center; border: 0; border-radius: 8px; background: transparent; color: #fff; cursor: pointer; }
</style>
